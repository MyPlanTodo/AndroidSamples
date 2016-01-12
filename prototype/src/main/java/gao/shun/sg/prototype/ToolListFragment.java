package gao.shun.sg.prototype;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AdapterView;

import java.util.ArrayList;

/**
 * Created by Theodore on 2016/1/5.
 */
public class ToolListFragment extends ListFragment implements AdapterView.OnItemClickListener {

    private static final String ARG_TOOL_TYPE = "toolType";
    private static final String ARG_TAB = "tab";

    private ToolType mToolType;
    private ToolPagerAdapter.Tab mTab;
    private ToolArrayAdapter mToolArrayAdapter;

    public ToolListFragment() {

    }

    public static ToolListFragment newInstance(ToolType toolType, ToolPagerAdapter.Tab tab) {
        final ToolListFragment fragment = new ToolListFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_TOOL_TYPE, toolType.name());
        args.putString(ARG_TAB, tab.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        if (args == null)
            throw new IllegalArgumentException("No arguments set; use newInstance when constructing!");
        mToolType = ToolType.valueOf(args.getString(ARG_TOOL_TYPE));
        mTab = ToolPagerAdapter.Tab.valueOf(args.getString(ARG_TAB));
        final ArrayList<Tool> tools = new ToolGenerator(mTab.hashCode()).getNewTools(mToolType, mTab, 20);
        mToolArrayAdapter = new ToolArrayAdapter(getActivity(), tools);
        setListAdapter(mToolArrayAdapter);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Tool tool = mToolArrayAdapter.getItem(position);
        ToolDetailActivity.startActivity(getActivity(), tool);
    }
}
