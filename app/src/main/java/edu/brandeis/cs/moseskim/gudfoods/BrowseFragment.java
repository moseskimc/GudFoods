package edu.brandeis.cs.moseskim.gudfoods;

/**
 * Created by Jon on 11/15/2016.
 */

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import edu.brandeis.cs.moseskim.gudfoods.aws.AWSService;
import edu.brandeis.cs.moseskim.gudfoods.aws.AmazonClientManager;
import edu.brandeis.cs.moseskim.gudfoods.aws.DynamoDBManager;
import edu.brandeis.cs.moseskim.gudfoods.aws.DynamoDBManagerTaskResult;
import edu.brandeis.cs.moseskim.gudfoods.aws.DynamoDBManagerType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class BrowseFragment extends Fragment  {

    private Button button;
    private Button settings;
    private ArrayList<String> idList = new ArrayList<String>();
    private ArrayList<FoodItem> entries = new ArrayList<FoodItem>();
    private ListView listView;
    private String token;
    private String username;
    double latitude;
    double longitude;
    private View rootView;
    private YelpService yelpService;
    private AsyncTask getYelpToken;
    private ProgressDialog pDialog;
    private static final int MY_PERMISSION_ACCESS_COURSE_LOCATION = 123;
    private MyLocationListener loc;
    private LocationManager locManager;
    private DynamoDBManager dynamoDBHelper;

    private Callback entriesCallback;
    private Callback finalCallback;
    private Callback findRestaurantsCallback;

    public static AmazonClientManager clientManager = null;
    private FoodItem fi;
    private boolean isSwipeRight;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.browse_fragment, container, false);
        button = (Button) rootView.findViewById(R.id.browse);
        settings = (Button) rootView.findViewById(R.id.signout);
        yelpService = new YelpService();
        listView = (ListView) rootView.findViewById(R.id.listView);
        username = getArguments().getString("username");

        clientManager = new AmazonClientManager(this.getActivity());

        //initialize entriesCallback, to be called after each restaurant api call
        entriesCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                entries.addAll(yelpService.getItems(response));
            }
        };

        //initialize finalCallback, to be called after final restaurant api call, also updates UI
        finalCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                entries.addAll(yelpService.getItems(response));
                if(response.isSuccessful()) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("uiEntries", "#" + entries.size());
                            CustomAdapter adapter = new CustomAdapter(getActivity(), entries);
                            listView.setAdapter(adapter);
                        }
                    });
                    pDialog.dismiss();
                }
            }
        };

        //general callback for yelpservice.findRestaurants()
        findRestaurantsCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("findRestaurants","OnResponse");

                //get a list of the restaurant ids using api call response
                idList = yelpService.getIDList(response);

                //clear entries list
                if(entries != null) {
                    entries.clear();
                }

                //for each id, make an api call and get some pictures, append to entries
                for (int i = 0; i <idList.size(); i++) {
                    if (i == idList.size() - 1) {
                        yelpService.pickImages(idList.get(i), token, finalCallback);
                    } else {
                        yelpService.pickImages(idList.get(i), token, entriesCallback);
                    }
                }
            }
        };
        loc = new MyLocationListener();
        locManager = (LocationManager)getActivity().getSystemService(Context.LOCATION_SERVICE);
        getYelpToken = new GetYelpToken().execute();
        pDialog = new ProgressDialog(getActivity());
        pDialog.setMessage("Loading...");
        pDialog.show();

        //set method to get photos onclick
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pDialog = new ProgressDialog(getActivity());
                pDialog.setMessage("Loading...");
                pDialog.show();
                findLocation();
                yelpService.findRestaurants(latitude, longitude, token, findRestaurantsCallback);
            }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AWSService.getPool().getUser(username).signOut();
                AWSService.setUser("");
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                fi = entries.get(position);
                isSwipeRight = true;
                new DynamoDBInsertUserSwipeTask().execute(DynamoDBManagerType.INSERT_USER_SWIPE);
            }
        });

        return rootView;
    }



    public void findLocation(){

        if ( ContextCompat.checkSelfPermission(getContext(),Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED){
            Toast.makeText(getActivity(),"Permission has been granted",Toast.LENGTH_SHORT).show();
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            Log.d("permission","granted");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,10,0,loc);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,loc);

            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            latitude = location.getLatitude();
            longitude = location.getLongitude();

            Log.d("Lat","" + latitude);
            Log.d("Long","" + longitude);
        }

        else{
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)){
                Toast.makeText(getActivity(),"Permission is needed to access location",Toast.LENGTH_SHORT).show();
            }
            requestPermissions(new String[] {  android.Manifest.permission.ACCESS_COARSE_LOCATION  },this.MY_PERMISSION_ACCESS_COURSE_LOCATION);
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {


        if (requestCode == MY_PERMISSION_ACCESS_COURSE_LOCATION){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                findLocation();
            }
            else{
                Toast.makeText(getActivity(),"Permission has been denied",Toast.LENGTH_SHORT).show();
            }
        }

        else{
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private class GetYelpToken extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection conn = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String yelpJsonStr = null;


            try {
                // Construct the URL
                URL url = new URL("https://api.yelp.com/oauth2/token");
                HashMap<String, String> parameters = new HashMap<String, String>();
                parameters.put("grant_type", "client_credentials");
                parameters.put("client_id", Constants.V3_CLIENT_ID);
                parameters.put("client_secret", Constants.V3_CLIENT_SECRET);
                Set set = parameters.entrySet();
                Iterator i = set.iterator();
                StringBuilder postData = new StringBuilder();
                for (Map.Entry<String, String> param : parameters.entrySet()) {
                    if (postData.length() != 0) {
                        postData.append('&');
                    }
                    postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                    postData.append('=');
                    postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
                }
                byte[] postDataBytes = postData.toString().getBytes("UTF-8");


                // Create the request and open the connection
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
                conn.setDoOutput(true);
                conn.getOutputStream().write(postDataBytes);

                Log.d("Response Code", "" + conn.getResponseCode());
                // Read the input stream into a String
                InputStream inputStream = conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                if (sb.length() == 0) {
                    return null;
                }
                yelpJsonStr = sb.toString();
                return yelpJsonStr;
            } catch (IOException e) {
                Log.e("PlaceholderFragment", "Error ", e);
                return null;
            } finally{
                if (conn != null) {
                    conn.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(s == null) {
                Log.d("response", "error");
            } else {
                try {
                    JSONObject obj = new JSONObject(s);
                    token = obj.getString("access_token");
                } catch (JSONException e){
                    Log.d("JSONException", e.toString());
                }
                Log.d("response", token);
                findLocation();
                yelpService.findRestaurants(latitude, longitude, token, findRestaurantsCallback);
            }
        }
    }

    private class DynamoDBInsertUserSwipeTask extends
            AsyncTask<DynamoDBManagerType, Void, DynamoDBManagerTaskResult> {

        protected DynamoDBManagerTaskResult doInBackground(
                DynamoDBManagerType... types) {

            String tableStatus = DynamoDBManager.getTestTableStatus();

            DynamoDBManagerTaskResult result = new DynamoDBManagerTaskResult();
            result.setTableStatus(tableStatus);
            result.setTaskType(types[0]);
            if (types[0] == DynamoDBManagerType.INSERT_USER_SWIPE) {
                if (tableStatus.equalsIgnoreCase("ACTIVE")) {
                    DynamoDBManager.incrementFoodItem(fi, isSwipeRight);
                    DynamoDBManager.insertUserSwipe(username, fi.getImageURL(), isSwipeRight);
                }
            }

            return result;
        }

        protected void onPostExecute(DynamoDBManagerTaskResult result) {
            if (result.getTaskType() == DynamoDBManagerType.LIST_USERS_SWIPES
                    && result.getTableStatus().equalsIgnoreCase("ACTIVE")) {
                new DynamoDBInsertUserSwipeTask().execute(DynamoDBManagerType.LIST_USERS_SWIPES);
            } else if (!result.getTableStatus().equalsIgnoreCase("ACTIVE")) {
                Toast.makeText(
                        BrowseFragment.this.getActivity(),
                        "The test table is not ready yet.\nTable Status: "
                                + result.getTableStatus(), Toast.LENGTH_LONG)
                        .show();
            }
        }
    }


}
