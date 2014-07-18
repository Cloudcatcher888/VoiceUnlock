package com.galih.voiceun;

import java.io.File;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;


public class AnyUnlock extends Activity implements OnClickListener
{
    private Button disableLockButton;
    private Button enableLockButton;
    private PendingIntent mAlarmSender;
    private AlarmManager am;
    private Handler mHandler;
    /* Magic number */
    private final static int REQ_CODE = 0x575f72a;
    private static File path = Environment.getExternalStorageDirectory();		
	static File foldersample = new File(path.getAbsolutePath()+"/VoiceUnlock/");	
	File samplefile = new File(foldersample,"codebook.ftr");
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setupUI();       
    }

    public void onClick(View v)
    {
        if(v == enableLockButton){
           askToActive();         

        }
        if(v == disableLockButton){
        	 
             am.cancel(mAlarmSender);
             disableAdmin();
             stopService(new Intent(getApplicationContext(), AnyUnlockService.class));
             mHandler.postDelayed(new Runnable(){
                 public void run(){
                     Process.killProcess(Process.myPid());
                 }
             }
             ,400);

         }
    }
    
      
    private void setupUI(){
    	  disableLockButton = (Button)findViewById(R.id.disable_button);
          enableLockButton = (Button)findViewById(R.id.enable_button);
          disableLockButton.setOnClickListener(this);
          enableLockButton.setOnClickListener(this);
          am = (AlarmManager)getSystemService(ALARM_SERVICE);
          mAlarmSender = PendingIntent.getService(this, REQ_CODE, new Intent(this, AnyUnlockService.class), 0);
          mHandler = new Handler();
    }

	public boolean onCreateOptionsMenu(Menu menu){
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_screen_menu, menu);
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
    	Intent myIntent = new Intent();
	    switch (item.getItemId()) {
        case R.id.settings:{
    		myIntent.setClass(this, SettingsScreen.class);
    		startActivity(myIntent);
            return true;
        }

    	case R.id.about: {
            new AlertDialog.Builder(this)
                .setTitle("About Voice Unlock")
                .setMessage("Voice Unlock\nAuthor: Liberty (Haowen Ning)\nEmail: liberty@anymemo.org\nEdited by @galrexa")
                .setPositiveButton("OK", null)
                .show();
                return true;
            }
	    }
	    return false;
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
            KeyguardManager.KeyguardLock kl = km.newKeyguardLock("Voice Unlock");
            kl.disableKeyguard();

        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    private void disableAdmin(){
        ComponentName mAdminReceiver= new ComponentName(this, AdminReceiver.class);
        DevicePolicyManager mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDPM.removeActiveAdmin(mAdminReceiver);
    }
    
    /**Menampilkan dialog jika otentikasi berhasil*/
    private void askToActive() {
    	AlertDialog.Builder builder = new Builder(this);
		builder.setTitle("Aktifkan Layanan");
		builder.setMessage("Anda yakin untuk aktifkan layanan Voice Unlock?");
		builder.setPositiveButton("Ya", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
	            Intent createSample = new Intent(getBaseContext(), CreateSample.class);
	            startActivity(createSample);	            
			}
			
		});
		builder.setNegativeButton("Tidak", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		builder.show();
	}

}
