package com.galih.voiceun;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.logging.Logger;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.galih.process.Codebook;
import com.galih.process.Constants;
import com.galih.process.FeatureVector;
import com.galih.process.KMeans;
import com.galih.process.MFCC;
import com.galih.process.Matrix;
import com.galih.wav.WavReader;
import com.galih.wav.WaveRecorder;
import com.google.gson.Gson;



/**
 * Membuat Codebook untuk voice sample. 
 * @author galihreksa
 *
 */
public class CreateSample extends Activity {
	static final String TAG = "VoiceAuth";

	private static final int DURASI = 2000;
	private static final int UI_REFRESH_TIME = 300;
	
	private EditText output;
	private Button tb_Rekam;
	private Button tb_Batal;
	private Button tb_Reset;
	private Button tb_Hitung;
	private Button tb_Simpan;
	private ProgressBar progressBar;	
	
	private Writer writer;
	public String codebookString;	
	private static final String PREF_KEY_MODE_ID = "modeId";
	private WaveRecorder rekamWAV;
	private long lastRecordStartTime;
	
	
	/** Output File rekam */
	private static File path = Environment.getExternalStorageDirectory();		
	static File foldersample = new File(path.getAbsolutePath()+"/VoiceUnlock/");	
	File samplefile = new File(foldersample,"sample.wav");
	private static final String codebookname = "codebook.ftr";
	
	
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
				new MfccTask(CreateSample.this).execute(samplefile.getAbsolutePath());				
				}
		}
	};


	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.createsample);		
		foldersample.mkdirs();
		setupUi();
	}
	
	private void setupUi() {
		tb_Rekam = (Button) findViewById(R.id.tbl_rekam);		
		tb_Hitung = (Button) findViewById(R.id.tbl_hitung);
		tb_Reset = (Button) findViewById(R.id.tbl_reset);		
		output = (EditText)findViewById(R.id.editText1);
		progressBar = (ProgressBar) findViewById(R.id.progress);
		progressBar.setMax(DURASI);
	}

	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.tbl_rekam:
			rekam();
			break;
		case R.id.tbl_hitung:
			hitungMfcc();
			samplefile.delete();
			break;		
		case R.id.tbl_reset:
			reset();
			break;		
		default:
			break;
		}
	}

	private void simpan() {		
		simpanFile(output.toString(),codebookname);		
	}

	private void hitungMfcc() {
		setButtonsEnabled(false, true, true, true, true);
		new MfccTask(this).execute(samplefile.getAbsolutePath());
	}

	private void batalRekam() {
		rekamWAV.stop();
		rekamWAV.release();
		rekamWAV.reset();
		updateUiHandler.removeMessages(0);
	}

	private void rekam() {		
		if (samplefile.exists()) samplefile.delete();
		rekamWAV = new WaveRecorder(8000);
		rekamWAV.setOutputFile(samplefile.getAbsolutePath());
		rekamWAV.prepare();
		rekamWAV.start();
		lastRecordStartTime = System.currentTimeMillis();
		updateUiHandler.sendEmptyMessage(0);
	}

	private void reset() {
		setButtonsEnabled(true, false, false, false, false);
		progressBar.setProgress(0);
	}
	
	private void setButtonsEnabled(boolean enableRekam, boolean enableHit, 
			boolean enableBatal, boolean enableReset, boolean enableSimpan) {
		tb_Rekam.setEnabled(enableRekam);
		tb_Hitung.setEnabled(enableHit);
		tb_Batal.setEnabled(enableBatal);
		tb_Reset.setEnabled(enableReset);
		tb_Simpan.setEnabled(enableSimpan);
	}

	/**
	 * Mencari sample .wav menjadi MFCC, dan merepresentasikan 
	 * nilainya kedalam codebook.
	 * 
	 * */
	class MfccTask extends AsyncTask<String, Object, String> {

		private ProgressDialog progressDialog;
		private final Activity parentActivity;
		
		public MfccTask(Activity parentActivity) {
			this.parentActivity = parentActivity;
		}

		@Override
		protected String doInBackground(String... params) {
			
			String filename = params[0];
			WavReader wavReader = new WavReader(filename);
			
			Log.i(TAG, "Membaca file " + filename);
			double[] samples = readSamples(wavReader);
			
			Log.i(TAG, "Menghitung MFCC");
			double[][] mfcc = calculateMfcc(samples);
			
				System.out.println(Double.isNaN(mfcc.length));
			
			FeatureVector pl = createFeatureVector(mfcc);
		
			KMeans kmeans = doClustering(pl);
			
			Codebook cb = createCodebook(kmeans);
			
			Gson gson = new Gson();
			
			String codebookJsonString = gson.toJson(cb, Codebook.class);
			Log.i("Output json = ", codebookJsonString);			
			return codebookJsonString;
		}

		private Codebook createCodebook(KMeans kmeans) {
			int numberClusters = kmeans.getNumberClusters();
			Matrix[] centers = new Matrix[numberClusters];
			for (int i = 0; i < numberClusters; i++) {
				centers[i] = kmeans.getCluster(i).getCenter();
			}
			Codebook cb = new Codebook();
			cb.setLength(numberClusters);
			cb.setCentroids(centers);
			return cb;
		}

		private KMeans doClustering(FeatureVector pl) {
			long start;
			KMeans kmeans = new KMeans(Constants.CLUSTER_COUNT, pl, Constants.CLUSTER_MAX_ITERATIONS);
			Log.i(TAG, "Prepared k means clustering");
			start = System.currentTimeMillis();
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			kmeans.run();
			Log.i(TAG, "Clustering finished, total time = " + (System.currentTimeMillis() - start) + "ms");
			return kmeans;
		}

		private FeatureVector createFeatureVector(double[][] mfcc) {
			int vectorSize = mfcc[0].length;
			int vectorCount = mfcc.length;
			Log.i(TAG, "Creating pointlist with dimension=" + vectorSize + ", count=" + vectorCount);
			FeatureVector pl = new FeatureVector(vectorSize, vectorCount);
			for (int i = 0; i < vectorCount; i++) {
				pl.add(mfcc[i]);
			}
			System.out.print(pl);
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

		private double[][] calculateMfcc(double[] samples) {
			MFCC mfccCalculator = new MFCC(Constants.SAMPLERATE, Constants.WINDOWSIZE,
					Constants.COEFFICIENTS, false, Constants.MINFREQ + 1, Constants.MAXFREQ, Constants.FILTERS);
			
			int hopSize = Constants.WINDOWSIZE / 2;
			int mfccCount = (samples.length / hopSize) - 1;
			double[][] mfcc = new double[mfccCount][Constants.COEFFICIENTS];
			long start = System.currentTimeMillis();
			for (int i = 0, pos = 0; pos < samples.length - hopSize; i++, pos += hopSize) {
				mfcc[i] = mfccCalculator.processWindow(samples, pos);
				if (i % 20 == 0) {
					publishProgress("Hitung features...", i, mfccCount);
				}
			}
			publishProgress("Hitung feature...", mfccCount, mfccCount);

			Log.i(TAG, "Calculated " + mfcc.length + " vectors of MFCCs in "
					+ (System.currentTimeMillis() - start) + "ms");
			return mfcc;
		}

		private double[] readSamples(WavReader wavReader) {
			int sampleSize = wavReader.getFrameSize();
			int sampleCount = wavReader.getPayloadLength() / sampleSize;
			int windowCount = (int) Math.floor(sampleCount / Constants.WINDOWSIZE);
			byte[] buffer = new byte[sampleSize];
			double[] samples = new double[windowCount
			                              * Constants.WINDOWSIZE];
			
			try {
				for (int i = 0; i < samples.length; i++) {
					wavReader.read(buffer, 0, sampleSize);
					samples[i] = createSample(buffer);
					
					if (i % 1000 == 0) {
						publishProgress("Membaca Sample...", i, samples.length);
					}
				}
			} catch (IOException e) {
				Log.e(CreateSample.TAG, "Exception in reading samples", e);
			}
			return samples;
		}
		
		@Override
		protected void onPostExecute(String result) {
			progressDialog.dismiss();
			codebookString = result;			
			Log.i("output", codebookString);				
			simpanFile(codebookString, codebookname);
			Intent Oten = new Intent(getBaseContext(), Testing.class);
			startActivity(Oten);
			
		}
		
		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(parentActivity);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setTitle("Tunggu...");
			progressDialog.setMessage("Tunggu...");
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
	
	private void checkExists(){
		// File out1 = new File(FILENAME);
			if (samplefile.exists()){
				Intent otentikasi = new Intent(this,Testing.class);
				startActivity(otentikasi);			
			}
			else{
				setupUi();
			}
	 }
	
	public static void simpanFile(final String data, String namafile) {
        
        try {
            FileWriter out = new FileWriter(new File(path+"/VoiceUnlock/", namafile));
            out.write(data);
            out.close();
        } catch (IOException e) {
        	Log.e(TAG, "Gagal, menyimpan");
        }
    }

    public static String readFileAsString(String fileName) {
        
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        BufferedReader in = null;

        try {
            in = new BufferedReader(new FileReader(new File(foldersample, fileName)));
            while ((line = in.readLine()) != null) stringBuilder.append(line);

        } catch (FileNotFoundException e) {
            Log.e(TAG, "Gagal membaca file");
        } catch (IOException e) {
            Log.e(TAG, "Gagal membuat folder");
        } 

        return stringBuilder.toString();
    }
    
    public static void writeFileOnSDCard(String strWrite, Context context,String fileName)
    {

            try 
            {
                    if (isSdReadable())   // isSdReadable()e method is define at bottom of the post
                    {
                            String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                            File myFile = new File(fullPath +"VoiceUnlock/"+fileName);

                            FileOutputStream fOut = new FileOutputStream(myFile);
                            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                            myOutWriter.append(strWrite);
                            myOutWriter.close();
                            fOut.close();
                    }
            }
            catch (Exception e)
            {
                    //do your stuff here
            }
    }
    
    public static boolean isSdReadable() 
    {

            boolean mExternalStorageAvailable = false;
            try 
            {
                    String state = Environment.getExternalStorageState();

                    if (Environment.MEDIA_MOUNTED.equals(state))
                    {
                            // We can read and write the media
                            mExternalStorageAvailable = true;
                            Log.i("isSdReadable", "External storage card is readable.");
                    }
                    else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) 
                    {
                            // We can only read the media
                            Log.i("isSdReadable", "External storage card is readable.");
                            mExternalStorageAvailable = true;
                    } 
                    else
                    {
                            // Something else is wrong. It may be one of many other
                            // states, but all we need to know is we can neither read nor
                            // write
                            mExternalStorageAvailable = false;
                    }
            } catch (Exception ex) 
            {

            }
            return mExternalStorageAvailable;
    }


}
