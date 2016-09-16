package jeromq;

import java.io.Serializable;

/**
 * Created by shun on 8/17/16.
 */

public class JeroMessage implements Serializable {
    private Channel channel;
    private String message;

    public JeroMessage(Channel channel, String message) {
        this.channel = channel;
        this.message = message;
    }

    public JeroMessage(String channel, String message) {
        this.channel = Channel.getChannelByName(channel);
        this.message = message;
    }

    public String getChannel() {
        return channel.toString();
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return getChannel() + " - " + getMessage();
    }
}