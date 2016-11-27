package edu.brandeis.cs.moseskim.gudfoods;

/**
 * Created by Jon on 11/15/2016.
 */

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import edu.brandeis.cs.moseskim.gudfoods.aws.DynamoDBManager;
import edu.brandeis.cs.moseskim.gudfoods.aws.DynamoDBManagerTaskResult;
import edu.brandeis.cs.moseskim.gudfoods.aws.DynamoDBManagerType;

public class SwipedListFragment extends Fragment {

    private View rootView;
    private ListView listView;
    private String username;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.swiped_list_fragment, container, false);
        listView = (ListView) rootView.findViewById(R.id.listView);

        username = getArguments().getString("username");
        new DynamoDBSwipedListTask().execute(DynamoDBManagerType.LIST_USERS_SWIPES);


        return rootView;
    }

    public class DynamoDBSwipedListTask extends
            AsyncTask<DynamoDBManagerType, Void, DynamoDBManagerTaskResult> {

        protected DynamoDBManagerTaskResult doInBackground(
                DynamoDBManagerType... types) {

            String tableStatus = DynamoDBManager.getTestTableStatus();

            DynamoDBManagerTaskResult result = new DynamoDBManagerTaskResult();
            result.setTableStatus(tableStatus);
            result.setTaskType(types[0]);

            if (types[0] == DynamoDBManagerType.LIST_USERS_SWIPES) {
                if (tableStatus.equalsIgnoreCase("ACTIVE")) {
                    DynamoDBManager.listUserSwipeRights(username);
                }
            } else if (types[0] == DynamoDBManagerType.REMOVE_USER_SWIPE) {
                if (tableStatus.equalsIgnoreCase("ACTIVE")) {

                }
            }

            return result;
        }

        protected void onPostExecute(DynamoDBManagerTaskResult result) {
            if (result.getTaskType() == DynamoDBManagerType.LIST_USERS_SWIPES
                    && result.getTableStatus().equalsIgnoreCase("ACTIVE")) {

            } else if (result.getTaskType() == DynamoDBManagerType.REMOVE_USER_SWIPE
                    && result.getTableStatus().equalsIgnoreCase("ACTIVE")) {
                // refresh list
            }else if (!result.getTableStatus().equalsIgnoreCase("ACTIVE")) {
                Toast.makeText(
                        SwipedListFragment.this.getActivity(),
                        "The test table is not ready yet.\nTable Status: "
                                + result.getTableStatus(), Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

}
