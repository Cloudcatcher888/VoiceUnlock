package com.galih.wav;




import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioSource;
import android.util.Log;

/**
 * This class lets the user record audio from the built-in microphone as .wav file (as opposed to
 * .3gpp file using the AMR-NB codec when recording with the default {@link MediaRecorder}).
 * 
 * @author Thomas Kaiser, AT
 */
public class WaveRecorder {
    /** Tag for logging. */
    private static final String TAG = "WaveRecorder";

    /**
     * INITIALIZING : recorder is initializing; READY : recorder has been initialized, recorder not
     * yet started RECORDING : recording ERROR : reconstruction needed STOPPED: reset needed.
     */
    public enum State {
        /** . */
        INISIALISASI,
        /** . */
        READY,
        /** . */
        REKAM,
        /** . */
        ERROR,
        /** . */
        STOP
    };

    /** Panjang Header WAV dalam byte */
    private static final int HEADER_LENGTH = 44;

    /** Selang waktu antara perekaman dan penyimpanan file WAV. */
    private static final int TIMER_INTERVAL = 10;

	private static final String DICTATE_TEMP_PATH = "./temp.wav";

    /** Class untuk merekam suara uncompressed */
    private AudioRecord aRecorder = null;

    /** Output file. */
    private String fPath = null;

    /** State perekaman {@link State}. */
    private State state;

    /** File writer. */
    private RandomAccessFile fWriter;

    // Inisialisasi awal format audio uncompress sesuai dengan parameter yang dibutuhkan
    /** Jumlah channel audio (1). */
    private short numChannels;
    /** Audio sampling rate. */
    private int sampleRate;
    /** Bits per sample. */
    private short bitsPerSample;
    /** Ukuran buffer audio. */
    private int bufferSize;
    /** Audio source, {@link AudioSource}. Dipilih Mic */
    private int audioSource;
    /** {@link AudioFormat}. */
    private int audioFormat;

    /** Penentuan jumlah frame pada setiap output block data. */
    private int framePeriod;

    /** Penentuan jumlah byte per block, untuk rekonstruksi ulang data */
    private int blockAlign;

    /** Buffer output. */
    private byte[] buffer;

    /**
     * Jumlah bye data yang disimpan setelah ditambahkan header WAV
     */
    private int payloadSize;

    /**
     * Gets set in start(), indicates that the user wanted to insert a record into an existing
     * dictate and a temp file containing the second part exists. This part will have to be appended
     * to the new dictate file in stop().
     */
    private boolean mIsInserting = false;

    /**
     * This listener gets notified periodically and writes the audio data from the buffer into the
     * file.
     */
    private AudioRecord.OnRecordPositionUpdateListener updateListener =
            new AudioRecord.OnRecordPositionUpdateListener() {
                public void onPeriodicNotification(AudioRecord recorder) {
                    // Log.v(TAG, "Update Listener called");
                    aRecorder.read(buffer, 0, buffer.length); // Fill buffer
                    try {
                        fWriter.write(buffer); // Write buffer to file
                        payloadSize += buffer.length;
                        // Log.v(TAG, "Written " + buffer.length
                        // + " bytes to file, new payload size = " + payloadSize);
                    } catch (IOException e) {
                        Log.w(TAG, "IOException occured in updateListener, state=" + state);
                    }
                }

                public void onMarkerReached(AudioRecord recorder) {
                    // NOT USED
                }
            };

    /**
     * Default constructor. Leaves the recorder in {@link State#INISIALISASI}, except if some kind
     * of error happens.
     * 
     * @param sampleRate
     *            Audio sampling rate.
     */
    public WaveRecorder(int sampleRate) {
        try {
            bitsPerSample = 16;

            numChannels = 1;

            audioSource = AudioSource.MIC;
            this.sampleRate = sampleRate;
            audioFormat = AudioFormat.ENCODING_PCM_16BIT;

            framePeriod = sampleRate * TIMER_INTERVAL / 1000;
            bufferSize = framePeriod * 2 * bitsPerSample * numChannels / 8;
            if (bufferSize < AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT)) {
                // increase buffer size if needed
                bufferSize =
                        AudioRecord.getMinBufferSize(sampleRate,
                                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                                AudioFormat.ENCODING_PCM_16BIT);
                // Set frame period and timer interval accordingly
                framePeriod = bufferSize / (2 * bitsPerSample * numChannels / 8);
                Log.w(TAG, "Increasing buffer size to " + bufferSize);
            }

            aRecorder =
                    new AudioRecord(audioSource, sampleRate,
                            AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize);
            if (aRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new Exception("AudioRecord initialization failed");
            }
            aRecorder.setRecordPositionUpdateListener(updateListener);
            aRecorder.setPositionNotificationPeriod(framePeriod);

            fPath = null;
            state = State.INISIALISASI;
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(TAG, e.getMessage());
            } else {
                Log.e(TAG, "Unknown error occured while initializing recording");
            }
            state = State.ERROR;
        }
    }

    /**
     * Returns the state of the recorder.
     * 
     * @return recorder state
     */
    public State getState() {
        return state;
    }

    /**
     * Sets the output file for the recorder, call in {@link State#INISIALISASI} , right after
     * constructing.
     * 
     * @param path
     *            Path of the output file.
     */
    public void setOutputFile(String path) {
        if (state == State.INISIALISASI) {
            fPath = path;
        } else {
            Log.e(TAG, "Output file can only be set in State=INITIALIZING, current state=" + state);
        }
    }

    /**
     * Prepares the recorder for recording, in case the recorder is not in the INITIALIZING state
     * and the file path was not set the recorder is set to the ERROR state, which makes a
     * reconstruction necessary. The header of the wave file is written. The file is DELETED! In
     * case of an exception, the state is changed to ERROR.
     */
    public void prepare() {
        try {
            if (state == State.INISIALISASI) {
                if ((aRecorder.getState() == AudioRecord.STATE_INITIALIZED) & (fPath != null)) {
                    // write file header

                    fWriter = new RandomAccessFile(fPath, "rw");

                    // this will clear out the file
                    fWriter.setLength(0);

                    // beginn RIFF header block
                    // WAVE header keyword
                    fWriter.writeBytes("RIFF");
                    // no file size known yet
                    fWriter.writeInt(0);
                    fWriter.writeBytes("WAVE");

                    // begin format block
                    fWriter.writeBytes("fmt ");
                    // format block is 16 bytes long
                    fWriter.writeInt(Integer.reverseBytes(16));
                    // "1" says we're writing PCM coded data
                    fWriter.writeShort(Short.reverseBytes((short) 1));
                    // channels
                    fWriter.writeShort(Short.reverseBytes(numChannels));
                    // sample rate
                    fWriter.writeInt(Integer.reverseBytes(sampleRate));
                    // byte rate
                    fWriter.writeInt(Integer.reverseBytes(sampleRate * bitsPerSample * numChannels
                            / 8));
                    // block align: how many bytes make up a single
                    // frame in the data block
                    blockAlign = (numChannels * bitsPerSample / 8);
                    fWriter.writeShort(Short.reverseBytes((short) blockAlign));
                    // bits per sample
                    fWriter.writeShort(Short.reverseBytes(bitsPerSample));

                    // begin data block
                    fWriter.writeBytes("data");
                    // payload size not known yet
                    fWriter.writeInt(0);

                    buffer = new byte[framePeriod * bitsPerSample / 8 * numChannels];
                    state = State.READY;

                    payloadSize = 0;
                } else {
                    Log.e(TAG, "prepare() method called on uninitialized recorder");
                    state = State.ERROR;
                }
            } else {
                Log.e(TAG, "prepare() method called on illegal state");
                release();
                state = State.ERROR;
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(TAG, e.getMessage(), e);
            } else {
                Log.e(TAG, "Unknown error occured in prepare()");
            }
            state = State.ERROR;
        }
    }

    /**
     * Releases the resources associated with this class, and removes the unnecessary files, when
     * necessary.
     */
    public void release() {
        if (state == State.REKAM) {
            stop();
        } else {
            if ((state == State.READY)) {
                try {
                    fWriter.close(); // Remove prepared file
                } catch (IOException e) {
                    Log.e(TAG, "I/O exception occured while closing output file");
                }
                (new File(fPath)).delete();
            }
        }

        if (aRecorder != null) {
            aRecorder.release();
        }
    }

    /**
     * Resets the recorder to the INITIALIZING state, as if it was just created. In case the class
     * was in RECORDING state, the recording is stopped. In case of exceptions the class is set to
     * the ERROR state.
     */
    public void reset() {
        try {
            if (state != State.ERROR) {
                release();
                fPath = null;
                aRecorder =
                        new AudioRecord(audioSource, sampleRate, numChannels + 1, audioFormat,
                                bufferSize);
                aRecorder.setRecordPositionUpdateListener(updateListener);
                aRecorder.setPositionNotificationPeriod(framePeriod);
                state = State.INISIALISASI;
            }
        } catch (Exception e) {
            Log.e(WaveRecorder.class.getName(), e.getMessage());
            state = State.ERROR;
        }
    }

    /**
     * Starts the recording, and sets the state to RECORDING. Call after prepare() for first time
     * recording or after stop() if you wish to continue recording.
     */
    public void start() {
        start(-1, false);
    }

    /**
     * See {@link #start()}, defaults to INSERTING a new part at the specified position.
     * 
     * @param fromPosition
     *            Lets you set a position in bytes to insert a new part into an existing record. Use
     *            -1 to append to end. Find out current record size in bytes with
     *            {@link #getRecordSize()}.
     */
    public void start(int fromPosition) {
        start(fromPosition, true);
    }

    /**
     * See {@link #start()}. Enables inserting a new part by specifying a position to start
     * recording from in bytes.
     * 
     * @param fromPosition
     *            See {@link #start(int)}.
     * @param insert
     *            Set true if you want to insert a new part, set false if the dicate should be
     *            overwritten from the specified position.
     */
    public void start(int fromPosition, boolean insert) {
        if (fromPosition < -1 || fromPosition > payloadSize + HEADER_LENGTH) {
            throw new IllegalArgumentException("fromPosition out of range: was " + fromPosition
                    + ", min/max=-1/" + (payloadSize + HEADER_LENGTH));
        }
        if (state == State.READY) {
            aRecorder.startRecording();
            aRecorder.read(buffer, 0, buffer.length);
            Log.i(TAG, "Started to record to " + fPath);
            state = State.REKAM;
        } else if (state == State.STOP) {
            // put the filewriter at the correct position and let's go
            try {
                long position = fWriter.length();
                if (fromPosition == -1) {
                    // easy case
                    position = fWriter.length();
                    mIsInserting = false;
                    Log.d(TAG, "Continuing record from end, file position =" + position);
                } else if (insert) {
                    // not easy case: create new file to copy&append to
                    position = fromPosition;
                    File currentRecord = new File(fPath);
                    File secondPart = new File(DICTATE_TEMP_PATH);

                    // little sanity check: if the user wants to insert at beginning,
                    // prevent the file header from getting copied away!
                    if (position < HEADER_LENGTH) {
                        Log.w(TAG, "Modifiying cutoff position from " + position + " to "
                                + (HEADER_LENGTH) + " to save the file header. ");
                        position = HEADER_LENGTH;
                    }

                    // round to nearest multiple of block alignment, otherwise
                    // the inserted part will be broken and can't be played back
                    long delta = position - HEADER_LENGTH;
                    if (delta % blockAlign != 0) {
                        long ceil = (delta + blockAlign - 1) / blockAlign * blockAlign;
                        Log.w(TAG, "Rounding up file position from " + (position) + " to "
                                + (ceil + HEADER_LENGTH));
                        position = ceil + HEADER_LENGTH;
                    }
                    copyFile(currentRecord, secondPart, (int) position, (int) fWriter.length());

                    // truncate the current file to append to
                    fWriter.setLength(position);
                    mIsInserting = true;
                    Log.d(TAG, "Inserting new part into dicate at position=" + position);
                } else {
                    // rewinding/overwrite case: just truncate the file
                    // and continue recording

                    // correct the payloadsize member
                    int delta = (int) (fWriter.length() - fromPosition);
                    Log.d(TAG, "Rewinding/overwriting dictate at position=" + fromPosition
                            + ", old payload = " + payloadSize + " - delta " + delta
                            + " = new payload " + (payloadSize - delta));
                    payloadSize -= delta;

                    position = fromPosition;
                    fWriter.setLength(position);
                    mIsInserting = false;
                }
                fWriter.seek(position);
                aRecorder.startRecording();
                aRecorder.read(buffer, 0, buffer.length);
                Log.i(TAG, "Continuing record to " + fPath + " from file position="
                        + fWriter.getFilePointer());
                state = State.REKAM;
            } catch (IOException e) {
                Log.e(TAG, "Couldn't move file pointer end of file", e);
            }
        } else {
            Log.e(TAG, "start() called on illegal state");
            state = State.ERROR;
        }
    }

    /**
     * Stops the recording, and sets the state to STOPPED. In case of further usage, a reset is
     * needed. Also finalizes the wave file in case of uncompressed recording.
     */
    public void stop() {
        if (state == State.REKAM) {
            aRecorder.stop();

            try {
                fWriter.seek(4); // Write filesize to header
                fWriter.writeInt(Integer.reverseBytes(36 + payloadSize));

                fWriter.seek(40); // Write payload size to header
                fWriter.writeInt(Integer.reverseBytes(payloadSize));
                Log.d(TAG, "Stopped recording with payloadSize=" + payloadSize
                        + ", was inserting? " + mIsInserting);
                // if we have been inserting, copy back the second part
                // of the file and correct the filesize values
                if (mIsInserting) {
                    // copy back from second part
                    File secondPart = new File(DICTATE_TEMP_PATH);
                    File record = new File(fPath);
                    appendFile(record, secondPart);
                    // we should theoretically get current sizes easily from
                    // our file writer
                    int totalSize = (int) fWriter.length();
                    int fileSize = totalSize - 8;
                    int dataSize = totalSize - 44;
                    Log
                            .i(TAG, "Appended file " + secondPart + " to record, totalSize="
                                    + totalSize);
                    fWriter.seek(4); // Write filesize to header
                    fWriter.writeInt(Integer.reverseBytes(fileSize));

                    fWriter.seek(40); // Write payload size to header
                    fWriter.writeInt(Integer.reverseBytes(dataSize));

                    // also correct the payloadSize member
                    payloadSize = dataSize;
                }

                Log.i(TAG, "Stopped recording, total payloadsize=" + payloadSize);

            } catch (IOException e) {
                Log.e(TAG, "I/O exception occured writing to output file in stop()");
                state = State.ERROR;
            }
            state = State.STOP;
        } else {
            Log.e(TAG, "stop() called on illegal state");
            state = State.ERROR;
        }
    }

    /**
     * Returns the recorded payload length of the .wav file.
     * 
     * @return Size of the recorded audio file (payload part, add 44 bytes to get total filesize) in
     *         bytes.
     */
    public int getRecordSize() {
        return payloadSize;
    }

    /**
     * Calculates the bitrate with current settings.
     * 
     * @return The bitrate per second.
     */
    public int getBitsPerSecond() {
        return sampleRate * bitsPerSample * numChannels;
    }

    /**
     * Copies a specified amount of bytes from a source to a destination file.
     * 
     * @param src
     *            Source file.
     * @param dst
     *            Destination file.
     * @param from
     *            Offset from beginning of source file.
     * @param to
     *            File position in bytes.
     * @throws IOException
     *             If anything goes wrong writing the file.
     */
    private void copyFile(File src, File dst, int from, int to) throws IOException {
        Log.d(TAG, "Copying file from " + src + " to " + dst + ", fromBytes=" + from + ", toBytes="
                + to);
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        in.skip(from);
        int total = (to - from);
        int read = 0;
        byte[] buf = new byte[100 * 1024];
        int len;
        while ((len = in.read(buf)) >= 0 && read < total) {
            if (len > 0) {
                out.write(buf, 0, len);
            }
            read += len;
        }
        out.flush();
        in.close();
        out.close();
    }

    /**
     * Appends the second file to the first one.
     * 
     * @param one
     *            .
     * @param two
     *            .
     * @throws IOException
     *             If anything goes wrong writing the file.
     */
    private void appendFile(File one, File two) throws IOException {
        InputStream in = new FileInputStream(two);
        OutputStream out = new FileOutputStream(one, true);
        byte[] buf = new byte[100 * 1024];
        int len;
        while ((len = in.read(buf)) >= 0) {
            if (len > 0) {
                out.write(buf, 0, len);
            }
        }
        out.flush();
        out.close();
        in.close();
    }
}

