package com.example.shreyas.s3integrationapp;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.cognito.CognitoSyncManager;
import com.amazonaws.mobileconnectors.cognito.Dataset;
import com.amazonaws.mobileconnectors.cognito.DefaultSyncCallback;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    AmazonS3 s3Client;
    String bucket = "s3androidintegration";
    File downloadFromS3;
    TransferUtility transferUtility;
    List<String> listing;
    ListView listView;
    ArrayAdapter<String> adapter;
    static final int MY_REQUEST_CODE = 1;
    File uploadToS3;

    String rootPath;
    private List<S3ObjectSummary> s3ObjList;
    boolean flag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView) findViewById(R.id.viewFiles);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, MY_REQUEST_CODE);
            }
        }
        else {
            prepareDirectory();
        }

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position,
                                    long arg3)
            {
                //String value = (String)adapter.getItemAtPosition(position);
                String key = (String) s3ObjList.get(position).getKey();
                rootPath = Environment.getExternalStorageDirectory().toString() + "/S3data";
                downloadFromS3 = new File(rootPath, key);
                downloadFileFromS3(key);
            }
        });

        // callback method to call credentialsProvider method.
        s3credentialsProvider();

        // callback method to call the setTransferUtility method
        setTransferUtility();

        sync();
    }

    public void s3credentialsProvider(){
        // Initialize the AWS Credential
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-east-1:cc082981-896f-42bc-ba26-5f284c9fcf18", // Identity Pool ID
                Regions.US_EAST_1 // Region
        );

        // Initialize the Cognito Sync client
        CognitoSyncManager syncClient = new CognitoSyncManager(getApplicationContext(), Regions.US_EAST_1, credentialsProvider);

        // Create a record in a dataset and synchronize with the server
        Dataset dataset = syncClient.openOrCreateDataset("myDataset");
        dataset.put("myKey", "myValue");
        dataset.synchronize(new DefaultSyncCallback() {
            @Override
            public void onSuccess(Dataset dataset, List newRecords) {
                //Your handler code here
            }
        });

        createAmazonS3Client(credentialsProvider);
    }

    /**
     *  Create a AmazonS3Client constructor and pass the credentialsProvider.
     * @param credentialsProvider
     */
    public void createAmazonS3Client(CognitoCachingCredentialsProvider credentialsProvider){

        // Create an S3 client
        s3Client = new AmazonS3Client(credentialsProvider);

        // Set the region of your S3 bucket
        s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));
    }

    public void setTransferUtility(){
        transferUtility = new TransferUtility(s3Client, this);
    }


    //Download the file to S3
    public void downloadFileFromS3(String key){

        TransferObserver transferObserver = transferUtility.download(
                bucket,     /* The bucket to download from */
                key,    /* The key for the object to download */
                downloadFromS3        /* The file to download the object to */
        );
        transferObserverListener(transferObserver);
    }

    public void sync(){
        final ProgressDialog progressDialog = ProgressDialog.show(MainActivity.this,null,"Synching");

        Thread thread = new Thread(new Runnable(){
            //@Override
            public void run() {

                try {
                    Looper.prepare();
                    listing = getObjectNamesForBucket(bucket, s3Client);

                    for (int i=0; i< listing.size(); i++){
                        Toast.makeText(MainActivity.this, listing.get(i),Toast.LENGTH_LONG).show();
                    }

                    s3ObjList = s3Client.listObjects(bucket).getObjectSummaries();
                    //Looper.loop();
                    // Log.e("tag", "listing "+ listing);
                    progressDialog.dismiss();
                }
                catch (Exception e) {
                    e.printStackTrace();
                    Log.e("tag", "Exception found while listing "+ e);
                }

            }
        });
        thread.start();


    }

    public void uploadFileToS3(View view){
        String upPath = Environment.getExternalStorageDirectory().toString() + "/Pictures/Twitter";
        uploadToS3 = new File(upPath, "IMG_20170515_132939.jpg");

        TransferObserver transferObserver = transferUtility.upload(
                bucket,     /* The bucket to upload to */
                "IMG_20170515_132939.jpg",    /* The key for the uploaded object */
                uploadToS3       /* The file where the data to upload exists */
        );

        transferObserverListener(transferObserver);
        sync();
        if(flag)
            adapter.notifyDataSetChanged();
    }

    public void fetchFileFromS3(View view){
        // display List of files from S3 Bucket
        flag = true;
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, listing);
        listView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    /**
     * @desc This method is used to return list of files name from S3 Bucket
     * @param bucket
     * @param s3Client
     * @return object with list of files
     */
    private List<String> getObjectNamesForBucket(String bucket, AmazonS3 s3Client) {
        ObjectListing objects=s3Client.listObjects(bucket);
        List<String> objectNames=new ArrayList<String>(objects.getObjectSummaries().size());
        Iterator<S3ObjectSummary> iterator=objects.getObjectSummaries().iterator();
        while (iterator.hasNext()) {
            objectNames.add(iterator.next().getKey());
        }
        while (objects.isTruncated()) {
            objects=s3Client.listNextBatchOfObjects(objects);
            iterator=objects.getObjectSummaries().iterator();
            while (iterator.hasNext()) {
                objectNames.add(iterator.next().getKey());
            }
        }
        return objectNames;
    }

    /**
     * This is listener method of the TransferObserver
     * Within this listener method, we get status of uploading and downloading file,
     * to display percentage of the part of file to be uploaded or downloaded to S3
     * It displays an error, when there is a problem in  uploading or downloading file to or from S3.
     */


    public void transferObserverListener(TransferObserver transferObserver){

        transferObserver.setTransferListener(new TransferListener(){

            @Override
            public void onStateChanged(int id, TransferState state) {
                Toast.makeText(getApplicationContext(), "State Change : "
                        + state, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                //int percentage = (int) (bytesCurrent/bytesTotal * 100);
                //Toast.makeText(getApplicationContext(), "Progress in %"
                        //+ percentage, Toast.LENGTH_SHORT).show();
                Toast.makeText(getApplicationContext(), "processing", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e("error","error");
            }

        });
    }

    public void prepareDirectory() {
        File folder = new File(Environment.getExternalStorageDirectory().toString()+"/S3data");
        boolean wasSuccessful = folder.mkdirs();
        if (wasSuccessful) {
            //Save the path as a string value
            String extStorageDirectory = folder.toString();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            //Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
            prepareDirectory();
            //Log.v(TAG,"DATA TRAINED");
        }
    }

}
