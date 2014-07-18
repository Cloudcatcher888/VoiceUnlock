package com.galih.voiceun;

import java.io.File;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;
import android.widget.ToggleButton;

public class Controller extends Activity{
	private ToggleButton toggle;
	private AlarmManager am;
    private Handler mHandler;
    private PendingIntent mAlarmSender;
    /* Magic number */
    private final static int REQ_CODE = 0x575f72e;
	
	private static File path = Environment.getExternalStorageDirectory();		
	static File foldersample = new File(path.getAbsolutePath()+"/VoiceUnlock/");	
	File samplefile = new File(foldersample,"sample.wav");
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.controller);
		checkExists();
		setupUI();
		
	}
	
	
	private void setupUI(){
		am = (AlarmManager)getSystemService(ALARM_SERVICE);
        mAlarmSender = PendingIntent.getService(this, REQ_CODE, new Intent(this, AnyUnlockService.class), 0);
        mHandler = new Handler();
		toggle = (ToggleButton) findViewById(R.id.toggleButton);
		toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		        if (isChecked) {
		            // The toggle is enabled
		        	Toast.makeText(getBaseContext(), "ENABLED", Toast.LENGTH_SHORT).show();
		        	long firstTime = SystemClock.elapsedRealtime();
		            am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, 600*1000, mAlarmSender);
		            enableAdmin();
		        } else {
		            // The toggle is disabled
		        	Toast.makeText(getBaseContext(), "Disabled!", Toast.LENGTH_SHORT).show();
		            am.cancel(mAlarmSender);
		            disableAdmin();
		            stopService(new Intent(getBaseContext(), AnyUnlockService.class));
		            mHandler.postDelayed(new Runnable(){
		                public void run(){
		                    Process.killProcess(Process.myPid());
		                }
		            }
		            ,400);
		        }
		    }
		});
		
	}
	
	
	
	private void checkExists(){
		// File out1 = new File(FILENAME);
			if (foldersample.exists()){
				//Intent otentikasi = new Intent(this,Utama.class);
				//startActivity(otentikasi);
				Toast.makeText(getBaseContext(), "ADA", Toast.LENGTH_SHORT).show();
			}
			else{
				setupUI();
			}
	 }
	
	@Override
    public void onResume(){
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        if(tm.getCallState() != 0){
            finish();
        }
        super.onResume();
    }

    private void enableAdmin(){
        Intent myIntent = new Intent(this, AdminPermissionSet.class);
        startActivityForResult(myIntent, 18);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 18){
            KeyguardManager km = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
            KeyguardManager.KeyguardLock kl = km.newKeyguardLock("AnyUnlock");
            kl.disableKeyguard();

        }

        super.onActivityResult(requestCode, resultCode, data);
    }

	
	 private void disableAdmin(){
	        ComponentName mAdminReceiver= new ComponentName(this, AdminReceiver.class);
	        DevicePolicyManager mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
	        mDPM.removeActiveAdmin(mAdminReceiver);
	    }

}
