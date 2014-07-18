package com.galih.voiceun;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.galih.process.ClusterUtil;
import com.galih.process.Codebook;
import com.galih.process.Constants;
import com.galih.process.FeatureVector;
import com.galih.process.MFCC;
import com.galih.voiceun.Testing.MfccTask;
import com.galih.wav.WavReader;
import com.galih.wav.WaveRecorder;
import com.google.gson.Gson;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;



public class LockScreen extends Activity{
    private DateFormat dateFormatter = DateFormat.getDateInstance();
    private DateFormat timeFormatter = DateFormat.getTimeInstance();
    private DateFormat dayOfWeekFormatter = new SimpleDateFormat("E/F");
    private TelephonyManager tm;
    private TextView timeView;
    private TextView dateView;
    private TextView dowView;
    private TextView batteryView;
    private ProgressBar progressBar;
    private Button tbRekam;
    private Handler mHandler;
    private long mStartTime;
    private static String TAG = "LockScreen";
    
    public FeatureVector userFeatureVector;
    private static final int DURASI = 2000;
    private static final double THRESHOLD = 60;	
    private long lastRecordStartTime;
    private WaveRecorder rekamWAV;
    
    //Inisialisasi folder dan nama file
    private static final String codebookfile = "/VoiceUnlock/codebook.ftr";	
	private static File path = Environment.getExternalStorageDirectory();		
	private static File foldertrain = new File(path.getAbsolutePath()+"/VoiceUnlock/");
	private static File training = new File(foldertrain,"train.wav");
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	requestWindowFeature(Window.FEATURE_NO_TITLE);
    	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
    			WindowManager.LayoutParams.FLAG_FULLSCREEN);
    	onAttachedToWindow();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lock_screen);
        tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        dateView = (TextView)findViewById(R.id.date);
        timeView = (TextView)findViewById(R.id.time);
        timeView = (TextView)findViewById(R.id.time);
        dowView = (TextView)findViewById(R.id.dow);
        batteryView = (TextView)findViewById(R.id.battery);
        mHandler = new Handler();
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressBar.setMax(DURASI);
        tbRekam = (Button)findViewById(R.id.tbRekam);
       
        tbRekam.setOnClickListener(new OnClickListener() {
			
    		/**Mulai merekam suara*/
			@Override
			public void onClick(View arg0) {
				Record();						
			}
		});
        
    }
    
    /**Mengatasi proses perekaman berdasar progressbar*/
    private Handler updateUiHandler = new Handler() {
		public void handleMessage(Message msg) {
			int elapsed = (int) (System.currentTimeMillis() - lastRecordStartTime);
			if (elapsed < DURASI) { 
				progressBar.setProgress(elapsed);
				sendEmptyMessageDelayed(0, 200);
			} else {
				rekamWAV.stop();
				progressBar.setProgress(progressBar.getMax());
				//finish();
				new MfccTask(LockScreen.this).execute(training.getAbsolutePath());	
				try {
					checkResults();
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				tbRekam.setEnabled(true);
				training.delete();
				
			}
		}
	};
    
        
    @Override
    public void onResume(){
        Date nd = Calendar.getInstance().getTime();
        dateView.setText(dateFormatter.format(nd));
        timeView.setText(timeFormatter.format(nd));
        dowView.setText(dayOfWeekFormatter.format(nd));
        if(tm.getCallState() != 0){
            finish();
        }
        mStartTime = System.currentTimeMillis();
        mHandler.removeCallbacks(mUpdateTimeTask);
        mHandler.postDelayed(mUpdateTimeTask, 100);

        super.onResume();
    }
    @Override
    public void onPause(){
        mHandler.removeCallbacks(mUpdateTimeTask);
        super.onPause();
    }

    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            long start = SystemClock.uptimeMillis();
            Date nd = Calendar.getInstance().getTime();
            timeView.setText(timeFormatter.format(nd));
            mHandler.postDelayed(this, 1000);
        }
    };
   
    private void Record(){
    	
    	//tbRekam.setEnabled(false);		
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
			} else {
				Toast.makeText(getApplicationContext(), "Berhasil", Toast.LENGTH_SHORT).show();
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
			System.out.print("out: "+pl);			
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
					publishProgress("Calculating features...", i, mfccCount);
				}
			}
			publishProgress("Calculating features...", mfccCount, mfccCount);

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
						publishProgress("Reading samples...", i, samples.length);
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
		}
		
		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(parentActivity);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setTitle("Working...");
			progressDialog.setMessage("Working...");
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
	
  //Matikan tombol Key
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if(keyCode==KeyEvent.KEYCODE_BACK)
    	{
    		Log.d("Test", "Back");
    		
    	}
    	if (keyCode==KeyEvent.KEYCODE_HOME){
    		
    	}
        return false;
    }
    
    public void onAttachedToWindow() {	      
		super.onAttachedToWindow();    
		this.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD);
		}
    
	
    
}


