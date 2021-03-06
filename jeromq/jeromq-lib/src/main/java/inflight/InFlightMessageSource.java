package inflight;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jeromq.Channel;
import jeromq.JeroMessage;
import jeromq.Publisher;
import jeromq.Subscriber;

/**
 * Created by shun on 9/2/16.
 */

public class InFlightMessageSource implements Subscriber.OnJeroMessageReceiveListener {

    private static final String TAG = InFlightMessageSource.class.getSimpleName();

    private static final int THREAD_POOL_SIZE = 3;
    private static final int INTERNAL_THREAD_SIZE = 3;
    private static final int INCOMING_QUEUE_SIZE = 1024;
    private static final int COMMAND_QUEUE_SIZE = 16;
    private static final int PORT = 6000;

    private boolean mRunning;
    private Publisher mPublisher;
    private Map<String, Subscriber> mSubscribers;
    private ExecutorService mExecutorService;
    private ExecutorService mExecutorServiceSubscribers;
    private BlockingQueue<JeroMessage> mIncomingMessage;
    private BlockingQueue<Command> mCommands;

//    private OnInFlightMessageReceiveListener mOnInFlightMessageReceiveListener;
//    private OnRequestMessageReceiveListener mOnRequestMessageReceiveListener;
//    private OnPropertySyncMessageReceiveListener mOnPropertySyncMessageReceiveListener;
//    private OnEventMessageReceiveListener mOnEventMessageReceiveListener;

    private InFlightSourceListener mInFlightSourceListener;

    public enum InFlightMessageSourceError {
        ERROR_PAYLOAD_JSON_FORMAT,
        ERROR_SUBSCRIBER_SIZE_LIMIT,
        ERROR_PUBLISHING_INVALID_MESSAGE
    }

    public InFlightMessageSource(String ip) {
        mExecutorService = Executors.newFixedThreadPool(INTERNAL_THREAD_SIZE);
        mExecutorServiceSubscribers = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        mSubscribers = new HashMap<>();
        mIncomingMessage = new ArrayBlockingQueue<>(INCOMING_QUEUE_SIZE);
        mCommands = new ArrayBlockingQueue<>(COMMAND_QUEUE_SIZE);

        mRunning = true;
        mExecutorService.execute(mMessageHandler);
        mExecutorService.execute(mCommander);
        postCommand(newStartPublisher(ip));
    }

    public void connect(String ip) {
        if (!mSubscribers.containsKey(ip)) {
            if (mSubscribers.size() >= THREAD_POOL_SIZE) {
                if (mInFlightSourceListener != null) mInFlightSourceListener.onInFlightSourceError(InFlightMessageSourceError.ERROR_SUBSCRIBER_SIZE_LIMIT);
                return;
            }
            postCommand(newConnectCommand(ip));
        }
    }

    public void subscribe(Channel channel) {
        postCommand(newSubscribeCommand(channel));
    }

    public void unSubscribe(Channel channel) {
        postCommand(newUnsubscribeCommand(channel));
    }

    public void stop() {
        postCommand(newStopCommand());
    }

    @Override
    public void onJeroMessageReceived(JeroMessage jeroMessage) {
        mIncomingMessage.add(jeroMessage);
    }

    public void publishMessage(Channel channel, InFlightMessage inFlightMessage) {
        if (inFlightMessage == null) {
            if (mInFlightSourceListener != null) mInFlightSourceListener.onInFlightSourceError(InFlightMessageSourceError.ERROR_PUBLISHING_INVALID_MESSAGE);
        }
        else if (mPublisher != null) mPublisher.publishMessage(channel, inFlightMessage.toJson());
    }

    private Runnable mMessageHandler = new Runnable() {
        @Override
        public void run() {
            mRunning = true;
            while (mRunning) {
                try {
                    JeroMessage jeroMessage = mIncomingMessage.take();
                    JSONObject json = new JSONObject(jeroMessage.getMessage());
                    InFlightMessage inFlightMessage = InFlightMessage.toInFlightMessage(json);
                    if (mInFlightSourceListener != null) {
                        if (mInFlightSourceListener instanceof OnRequestMessageReceiveListener)
                            ((OnRequestMessageReceiveListener) mInFlightSourceListener).onRequestMessageReceived(jeroMessage.getChannel(), (MessageRequest) inFlightMessage);
                        else if (mInFlightSourceListener instanceof OnPropertySyncMessageReceiveListener)
                            ((OnPropertySyncMessageReceiveListener) mInFlightSourceListener).onPropertySyncMessageReceived(jeroMessage.getChannel(), (MessagePropertySync) inFlightMessage);
                        else if (mInFlightSourceListener instanceof OnEventMessageReceiveListener)
                            ((OnEventMessageReceiveListener) mInFlightSourceListener).onEventMessageReceived(jeroMessage.getChannel(), (MessageEvent) inFlightMessage);
                        else if (mInFlightSourceListener instanceof OnInFlightMessageReceiveListener)
                            ((OnInFlightMessageReceiveListener) mInFlightSourceListener).onInFlightMessageReceived(jeroMessage.getChannel(), inFlightMessage);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                    if (mInFlightSourceListener != null) mInFlightSourceListener.onInFlightSourceError(InFlightMessageSourceError.ERROR_PAYLOAD_JSON_FORMAT);
                }
            }
            Log.v(TAG, "~mMessageHandler.run()");
        }
    };

//    public void setOnInFlightMessageReceiveListener(OnInFlightMessageReceiveListener onInFlightMessageReceiveListener) {
//        mOnInFlightMessageReceiveListener = onInFlightMessageReceiveListener;
//    }
//
//    public void setOnRequestMessageReceiveListener(OnRequestMessageReceiveListener onRequestMessageReceiveListener) {
//        mOnRequestMessageReceiveListener = onRequestMessageReceiveListener;
//    }
//
//    public void setOnPropertySyncMessageReceiveListener(OnPropertySyncMessageReceiveListener onPropertySyncMessageReceiveListener) {
//        mOnPropertySyncMessageReceiveListener = onPropertySyncMessageReceiveListener;
//    }
//
//    public void setOnEventMessageReceiveListener(OnEventMessageReceiveListener onEventMessageReceiveListener) {
//        mOnEventMessageReceiveListener = onEventMessageReceiveListener;
//    }

    public void setInFlightSourceListener(InFlightSourceListener inFlightSourceListener) {
        mInFlightSourceListener = inFlightSourceListener;
    }

    public interface InFlightSourceListener {
        void onInFlightSourceError(InFlightMessageSourceError error);
    }

    public interface OnInFlightMessageReceiveListener extends InFlightSourceListener {
        void onInFlightMessageReceived(Channel channel, InFlightMessage inFlightMessage);
    }

    public interface OnRequestMessageReceiveListener extends InFlightSourceListener {
        void onRequestMessageReceived(Channel channel, MessageRequest request);
    }

    public interface OnPropertySyncMessageReceiveListener extends InFlightSourceListener {
        void onPropertySyncMessageReceived(Channel channel, MessagePropertySync propertySync);
    }

    public interface OnEventMessageReceiveListener extends InFlightSourceListener {
        void onEventMessageReceived(Channel channel, MessageEvent event);
    }

    private enum CommandType {
        START_PUBLISHER, CONNECT, STOP, SUBSCRIBE, UNSUBSCRIBE
    }

    private class Command {
        CommandType commandType;
        String ip;
        Channel channel;
    }

    private Runnable mCommander = new Runnable() {
        @Override
        public void run() {
            while (mRunning) {
                try {
                    Command command = mCommands.take();
                    switch (command.commandType) {
                        case STOP:
                            doStop();
                            break;

                        case CONNECT:
                            doConnect(command.ip);
                            break;

                        case SUBSCRIBE:
                            doSubscribe(command.channel);
                            break;

                        case UNSUBSCRIBE:
                            doUnsubscribe(command.channel);
                            break;

                        case START_PUBLISHER:
                            doStartPublisher(command.ip);
                            break;

                        default:
                            break;

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void doStop() {
        mRunning = false;

        mPublisher.interrupt();
        mPublisher.stopPublisher();
        mPublisher = null;

        mCommands.clear();
        mCommands = null;

        mIncomingMessage.clear();
        mIncomingMessage = null;

        for (Subscriber subscriber: mSubscribers.values()) subscriber.stopSubscriber();
        mSubscribers.clear();
        mSubscribers = null;

        mExecutorServiceSubscribers.shutdown();
        mExecutorServiceSubscribers = null;

        mExecutorService.shutdown();
        mExecutorService = null;

        Log.v(TAG, "after doStop()");
    }

    private void doConnect(String ip) {
        Subscriber subscriber = new Subscriber(ip, PORT);
        subscriber.setOnJeroMessageReceiveListener(this);
        mExecutorServiceSubscribers.execute(subscriber);
        mSubscribers.put(ip, subscriber);
    }

    private void doSubscribe(Channel channel) {
        for (Subscriber subscriber: mSubscribers.values()) subscriber.subscribe(channel);
    }

    private void doUnsubscribe(Channel channel) {
        for (Subscriber subscriber: mSubscribers.values()) subscriber.unsubscribe(channel);
    }

    private void doStartPublisher(String ip) {
        mPublisher = new Publisher(ip, PORT);
        mPublisher.startPublisher();
        mExecutorService.execute(mPublisher);
    }

    private void postCommand(Command command) {
        mCommands.add(command);
    }

    private Command newConnectCommand(String ip) {
        Command command = new Command();
        command.commandType = CommandType.CONNECT;
        command.ip = ip;
        return command;
    }

    private Command newStopCommand() {
        Command command = new Command();
        command.commandType = CommandType.STOP;
        return command;
    }

    private Command newSubscribeCommand(Channel channel) {
        Command command = new Command();
        command.commandType = CommandType.SUBSCRIBE;
        command.channel = channel;
        return command;
    }

    private Command newUnsubscribeCommand(Channel channel) {
        Command command = new Command();
        command.commandType = CommandType.UNSUBSCRIBE;
        command.channel = channel;
        return command;
    }

    private Command newStartPublisher(String ip) {
        Command command = new Command();
        command.commandType = CommandType.START_PUBLISHER;
        command.ip = ip;
        return command;
    }

}
