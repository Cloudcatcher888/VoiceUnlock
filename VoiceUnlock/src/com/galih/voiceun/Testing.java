package com.galih.voiceun;




import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.galih.process.Constants;
import com.galih.process.MFCC;
import com.galih.wav.WavReader;
import com.galih.process.FeatureVector;
import com.galih.process.ClusterUtil;
import com.galih.process.Codebook;
import com.galih.wav.WaveRecorder;
import com.google.gson.Gson;

public class Testing extends Activity {
	public static final String TAG = "VoiceAuth";
	private Button tbRekam, tbCek;
	private ProgressBar progressBar;
	private PendingIntent mAlarmSender;
    private AlarmManager am;
    private Handler mHandler;
    private final static int REQ_CODE = 0x575f72b;
    private static final int DURASI = 2000;
    private static final double THRESHOLD = 60;	
    private long lastRecordStartTime;
    private WaveRecorder rekamWAV;
    public FeatureVector userFeatureVector;    
	private static final int UI_REFRESH_TIME = 300;
    private double[][] o;
    //Inisialisasi folder dan nama file
    private static final String codebookfile = "/VoiceUnlock/codebook.ftr";	
	private static File path = Environment.getExternalStorageDirectory();		
	private static File foldertrain = new File(path.getAbsolutePath()+"/VoiceUnlock/");
	File training = new File(foldertrain,"train.wav");
    
	/** Stop rekam suara setelah durasi (progress bar) selesai.*/
	private Handler updateUiHandler = new Handler() {
		public void handleMessage(Message msg) {
			int elapsed = (int) (System.currentTimeMillis() - lastRecordStartTime);
			if (elapsed < DURASI) {
				progressBar.setProgress(elapsed);
				sendEmptyMessageDelayed(0, UI_REFRESH_TIME);
			} else {
				rekamWAV.stop();
				progressBar.setProgress(progressBar.getMax());
				new MfccTask(Testing.this).execute(training.getAbsolutePath());
				
				tbRekam.setEnabled(true);
				tbCek.setEnabled(true);
				}
		}
	};
    
    public void onCreate(Bundle savedInstanceState){
    	super.onCreate(savedInstanceState);
    	setContentView(R.layout.testing);
    	progressBar = (ProgressBar) findViewById(R.id.progressBar1);		
		progressBar.setMax(DURASI);
    	tbRekam = (Button)findViewById(R.id.btKlik);
    	
    	tbCek = (Button)findViewById(R.id.btCek);
    	
    	am = (AlarmManager)getSystemService(ALARM_SERVICE);
        mAlarmSender = PendingIntent.getService(this, REQ_CODE, new Intent(this, AnyUnlockService.class), 0);
        mHandler = new Handler();        
    	
    }
    
    public void onClick(View v){
		switch (v.getId()) {
		case R.id.btKlik:
			startRecording();
			break;
		case R.id.btCek:
			try {
				checkResults();
			} catch (Exception e) {				
				e.printStackTrace();
			}
			break;
		default:
			break;
		}
	}    
    
       
    /**Rekam Suara*/
    private void startRecording() {
		tbRekam.setEnabled(false);
		tbCek.setEnabled(false);
		rekamWAV = new WaveRecorder(8000);
		rekamWAV.setOutputFile(training.getAbsolutePath());
		rekamWAV.prepare();
		rekamWAV.start();
		lastRecordStartTime = System.currentTimeMillis();
		updateUiHandler.sendEmptyMessage(0);
	}
    /**Cek hasil perbandingan*/
    private void checkResults() throws Exception {
		double minAverageDistortion = Double.MAX_VALUE;
		Codebook codebook = getCodebookForUser();
		double averageDistortion = ClusterUtil.calculateAverageDistortion(userFeatureVector, codebook);
		Log.d("VOICEUNLOCK", "Calculated avg distortion =" + averageDistortion);
		if (averageDistortion < minAverageDistortion) {
			minAverageDistortion = averageDistortion;
			}
		if (minAverageDistortion < THRESHOLD) {
			Toast.makeText(getApplicationContext(), "Gagal", Toast.LENGTH_SHORT).show();
			training.delete();
			} else {
				Toast.makeText(getApplicationContext(), "Berhasil", Toast.LENGTH_SHORT).show();
				Toast.makeText(getApplicationContext(), "Voice Unlock Aktif", Toast.LENGTH_SHORT).show();
				long firstTime = SystemClock.elapsedRealtime();
				am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, 600*1000, mAlarmSender);
	            enableAdmin();
	            finish();
				}
		}
    /**Memanggil codebook dari file*/
    private Codebook getCodebookForUser() throws Exception {
		Gson gson = new Gson();
		String representation = getStringFromFile();
		Codebook codebook = gson.fromJson(representation,Codebook.class);
		return codebook;
	}
    
    /**Inti proses EKstraksi suara*/
    class MfccTask extends AsyncTask<String, Object, FeatureVector> {
		private ProgressDialog progressDialog;
		private final Activity parentActivity;
		
		public MfccTask(Activity parentActivity) {
			this.parentActivity = parentActivity;
		}

		@Override
		protected FeatureVector doInBackground(String... params) {
			String filename = params[0];
			WavReader wavReader = new WavReader(filename);
			
			Log.i(TAG, "Membaca file " + filename);
			double[] samples = readSamples(wavReader);

			Log.i(TAG, "Menghitung MFCC");
			double[][] mfcc = hitungMfcc(samples);
			
			FeatureVector pl = createFeatureVector(mfcc);
			double[][]res = mfcc;
			System.out.print(res);
			return pl;
		}


		private FeatureVector createFeatureVector(double[][] mfcc) {
			int vectorSize = mfcc[0].length;
			int vectorCount = mfcc.length;
			Log.i(TAG, "Creating pointlist with dimension=" + vectorSize + ", count=" + vectorCount);
			FeatureVector pl = new FeatureVector(vectorSize, vectorCount);
			for (int i = 0; i < vectorCount; i++) {
				pl.add(mfcc[i]);
			}
			Log.d(CreateSample.TAG, "Added all MFCC vectors to pointlist");
			return pl;
		}

		private short createSample(byte[] buffer) {
			short sample = 0;
			short b1 = buffer[0];
			short b2 = buffer[1];
			b2 <<= 8;
			sample = (short) (b1 | b2);
			return sample;
		}

		private double[][] hitungMfcc(double[] samples) {
			MFCC mfccCalculator = new MFCC(Constants.SAMPLERATE, Constants.WINDOWSIZE,
					Constants.COEFFICIENTS, false, Constants.MINFREQ + 1, Constants.MAXFREQ, Constants.FILTERS);
			
			int hopSize = Constants.WINDOWSIZE / 2;
			int mfccCount = (samples.length / hopSize) - 1;
			double[][] mfcc = new double[mfccCount][Constants.COEFFICIENTS];
			long start = System.currentTimeMillis();
			for (int i = 0, pos = 0; pos < samples.length - hopSize; i++, pos += hopSize) {
				mfcc[i] = mfccCalculator.processWindow(samples, pos);
				if (i % 20 == 0) {
					publishProgress("Menghitung feature...", i, mfccCount);
				}
			}
			publishProgress("Menghitung feature...", mfccCount, mfccCount);

			Log.i(TAG, "Calculated " + mfcc.length + " vectors of MFCCs in "
					+ (System.currentTimeMillis() - start) + "ms");
			return mfcc;
		}

		private double[] readSamples(WavReader wavReader) {
			int sampleSize = wavReader.getFrameSize();
			int sampleCount = wavReader.getPayloadLength() / sampleSize;
			int windowCount = (int) Math.floor(sampleCount / Constants.WINDOWSIZE);
			byte[] buffer = new byte[sampleSize];
			double[] samples = new double[windowCount * Constants.WINDOWSIZE];
			
			try {
				for (int i = 0; i < samples.length; i++) {
					wavReader.read(buffer, 0, sampleSize);
					samples[i] = createSample(buffer);
					
					if (i % 1000 == 0) {
						publishProgress("Membaca sample...", i, samples.length);
					}
				}
			} catch (IOException e) {
				Log.e(CreateSample.TAG, "Exception in reading samples", e);
			}
			return samples;
		}
		
		@Override
		protected void onPostExecute(FeatureVector result) {
			progressDialog.dismiss();
			userFeatureVector = result;
			System.out.print(o);
			System.out.print(result);
		}
		
		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(parentActivity);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setTitle("Proses...");
			progressDialog.setMessage("Proses...");
			progressDialog.setProgress(0);
			progressDialog.setMax(10000);
			progressDialog.show();
		}
		
		@Override
		protected void onProgressUpdate(Object... values) {
			String msg = (String) values[0];
			Integer current = (Integer) values[1];
			Integer max = (Integer) values[2];
			
			progressDialog.setMessage(msg);
			progressDialog.setProgress(current);
			progressDialog.setMax(max);
		}
		
	}
    
    
    /**Membaca stream data menjadi Sring*/
    public static String convertStreamToString(InputStream is) throws Exception {
	    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
	    StringBuilder sb = new StringBuilder();
	    String line = null;
	    while ((line = reader.readLine()) != null) {
	      sb.append(line).append("\n");
	    }
	    reader.close();
	    return sb.toString();
	}

    /**Membaca String dari file*/
    public static String getStringFromFile () throws Exception {
	    File fl = new File(foldertrain.toString()+"/codebook.ftr");
	    FileInputStream fin = new FileInputStream(fl);
	    String ret = convertStreamToString(fin);
	    //Make sure you close all streams.
	    fin.close();        
	    return ret;
	}
    
 //==================Bagian Service======================================
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

}

