/*
 * Copyright 2010-2012 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package edu.brandeis.cs.moseskim.gudfoods.aws.dynamodb;

import android.util.Log;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBScanExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.brandeis.cs.moseskim.gudfoods.Constants;
import edu.brandeis.cs.moseskim.gudfoods.FoodItem;
import edu.brandeis.cs.moseskim.gudfoods.MainActivity;
import edu.brandeis.cs.moseskim.gudfoods.TrendingFragment;
import edu.brandeis.cs.moseskim.gudfoods.aws.dynamodb.model.FoodItem_Dynamo;
import edu.brandeis.cs.moseskim.gudfoods.aws.dynamodb.model.UserBusiness_Dynamo;
import edu.brandeis.cs.moseskim.gudfoods.aws.dynamodb.model.UserSwipe_Dynamo;

public class DynamoDBManager {

    private static final String TAG = "DynamoDBManager";

    /*
     * Retrieves the table description and returns the table status as a string.
     */
    public static String getTestTableStatus() {

        try {

            AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();

            DescribeTableRequest request = new DescribeTableRequest()
                    .withTableName(Constants.TEST_TABLE_NAME);
            DescribeTableResult result = null;
            while (result == null) {
                try {
                    result = ddb.describeTable(request);
                } catch (AmazonClientException e) {
                    Log.e(TAG, "HTTP request timeout");
                }
            }

            String status = result.getTable().getTableStatus();
            return status == null ? "" : status;

        } catch (ResourceNotFoundException e) {
        } catch (AmazonServiceException ex) {
            MainActivity.clientManager
                    .wipeCredentialsOnAuthError(ex);
        }

        return "";
    }

    /**
     * Increments the swipeLeft or swipeRight count for a single food item
     * @param fi
     * @param isSwipeRight
     */
    public static void incrementFoodItem(FoodItem fi, boolean isSwipeRight) {
        AmazonDynamoDBClient ddb = MainActivity.clientManager
                .ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);

        Log.d(TAG, fi.toString());
        FoodItem_Dynamo foodItem = mapper.load(FoodItem_Dynamo.class, fi.getImageURL());
        if(foodItem == null) {
            foodItem = new FoodItem_Dynamo();
            foodItem.setImageURL(fi.getImageURL());
            foodItem.setBusinessName(fi.getBusinessName());
            foodItem.setBusinessId(fi.getBusinessId());
            foodItem.setPrice(fi.getPrice());
            foodItem.setRating(fi.getRating());
            if (isSwipeRight) {
                foodItem.setSwipeRightCount(1);
                foodItem.setSwipeLeftCount(0);
            } else {
                foodItem.setSwipeRightCount(0);
                foodItem.setSwipeLeftCount(1);
            }
            foodItem.setLatitude(fi.getLatitude());
            foodItem.setLongitude(fi.getLongitude());
        } else {
            if (isSwipeRight) {
                foodItem.setSwipeRightCount(foodItem.getSwipeRightCount() + 1);
            } else {
                foodItem.setSwipeLeftCount(foodItem.getSwipeLeftCount() + 1);
            }
        }
        mapper.save(foodItem);
    }

    public static void incrementFoodItem(FoodItem_Dynamo foodItemDynamo) {
        if (foodItemDynamo != null) {
            AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
            DynamoDBMapper mapper = new DynamoDBMapper(ddb);

            foodItemDynamo.setSwipeRightCount(foodItemDynamo.getSwipeRightCount() + 1);
            mapper.save(foodItemDynamo);
        }
    }

    public static void insertUserSwipe(String username, String foodURL, boolean isSwipeRight) {
        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);
        Log.d(TAG, "INSERT USER SWIPE " + username);

        UserSwipe_Dynamo userSwipe = new UserSwipe_Dynamo();
        userSwipe.setUserId(username);
        userSwipe.setFoodImageURL(foodURL);
        userSwipe.setSwipeRight(isSwipeRight);
        userSwipe.setDeleted(false);
        userSwipe.setTimeAdded(new Date());

        mapper.save(userSwipe);
    }

    public static void deleteUserSwipe(String username, String foodURL) {
        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);

        UserSwipe_Dynamo userSwipeDynamo = mapper.load(UserSwipe_Dynamo.class, username, foodURL);
        if (userSwipeDynamo != null) {
            userSwipeDynamo.setDeleted(true);
            mapper.save(userSwipeDynamo);
        }
    }

    public static int incrementImageIndex(String username, String businessId) {
        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);
        Log.d(TAG, "INCREMENT USER BUSINESS  " + username + " " +  businessId);

        UserBusiness_Dynamo userBusinessDynamo = mapper.load(UserBusiness_Dynamo.class, username, businessId);

        if (userBusinessDynamo == null) {
            userBusinessDynamo = new UserBusiness_Dynamo();
            userBusinessDynamo.setUser(username);
            userBusinessDynamo.setBusinessId(businessId);
            userBusinessDynamo.setImageIndex(1);
        } else {
            userBusinessDynamo.setImageIndex(userBusinessDynamo.getImageIndex() + 1);
        }

        mapper.save(userBusinessDynamo);
        Log.d("TAG", "" + userBusinessDynamo.getImageIndex());
        return userBusinessDynamo.getImageIndex();
   }

    public static int getImageIndex(String username, String businessId) {
        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);
        Log.d(TAG, "INCREMENT USER BUSINESS  " + username + " " +  businessId);

        UserBusiness_Dynamo userBusinessDynamo = mapper.load(UserBusiness_Dynamo.class, username, businessId);
        if (userBusinessDynamo != null) {
            return userBusinessDynamo.getImageIndex();
        } else {
            return 0;
        }
    }

    public static List<UserBusiness_Dynamo> getAllImageIndexes(String username) {
        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);
        Log.d(TAG, "Listing all USER BUSINESS  " + username );

        DynamoDBQueryExpression<UserBusiness_Dynamo> query = new DynamoDBQueryExpression<>();
        UserBusiness_Dynamo queryName = new UserBusiness_Dynamo();
        queryName.setUser(username);
        query.setHashKeyValues(queryName);


        PaginatedQueryList<UserBusiness_Dynamo> resultsList = mapper.query(UserBusiness_Dynamo.class, query);
        return resultsList;
    }

    public static List<UserSwipe_Dynamo> listUserSwipeRights(String username) {
        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);
        Log.d("LIST_USER_SWIPE_RIGHTS", username + "");

        DynamoDBQueryExpression<UserSwipe_Dynamo> query = new DynamoDBQueryExpression<>();
        UserSwipe_Dynamo test = new UserSwipe_Dynamo();
        test.setUserId(username);
        query.setHashKeyValues(test);


        PaginatedQueryList<UserSwipe_Dynamo> resultsList = mapper.query(UserSwipe_Dynamo.class, query);
        return resultsList;
    }

    public static Map<UserSwipe_Dynamo, FoodItem_Dynamo> listFoodItems(List<UserSwipe_Dynamo> list) {

        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);

        Map<UserSwipe_Dynamo, FoodItem_Dynamo> foodMap = new HashMap<>();
        FoodItem_Dynamo foodItem;
        for (UserSwipe_Dynamo u : list) {
                foodItem = mapper.load(FoodItem_Dynamo.class, u.getFoodImageURL());
                foodMap.put(u, foodItem);
        }

        return foodMap;
    }

    public static List<FoodItem_Dynamo> listTrendingFoodItems() {
        AmazonDynamoDBClient ddb = MainActivity.clientManager.ddb();
        DynamoDBMapper mapper = new DynamoDBMapper(ddb);

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        List<FoodItem_Dynamo> list = new ArrayList<>(mapper.scan(FoodItem_Dynamo.class, scanExpression));
        Collections.sort(list);
        list = list.subList(0, Math.min(list.size(), TrendingFragment.LIMIT));
        return list;
    }
}
