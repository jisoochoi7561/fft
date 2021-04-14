package com.zeroindexed.piedpiper;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.ArrayList;

public class Listentone {
    int HANDSHAKE_START_HZ = 8192;
    int HANDSHAKE_END_HZ = 8192 + 512;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    FastFourierTransformer transform;
    public AudioRecord mAudioRecord = null;
    public Listentone() {
        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
    }

    private int FindPowerSize(int size) {
        int count = 1,result = 0, sub_val = 0;
        while(true) {
            result = (int)Math.pow(2, count);
            sub_val = size - result;
            if(sub_val < 0) {
                break;
            }
            count++;
        }

        return (int)Math.pow(2, count-1);
    }

    public void Listen_main() {
        String StringData = null;
        this.mAudioRecord.startRecording();

        boolean in_packet = false;
        int blockSize = FindPowerSize(Math.round(interval / 2 * mSampleRate)); // Math.round(duration / 2 * sampling_rate)
                                                                            // 2를 곱하는 이유는 extract_packet에서 확인
        ArrayList<Double> packet = new ArrayList<>();
        short[] buffer = new short[blockSize];

        double[] toTransform = new double[blockSize];
        while (true) {
            int bufferedReadResult = mAudioRecord.read(buffer, 0, blockSize);
            if (bufferedReadResult < 0) break;

            for (int i = 0; i < blockSize && i < bufferedReadResult; i++) {
                toTransform[i] = (double) buffer[i];
            }

            double dom = dominant(toTransform);

            if(in_packet && match(dom,HANDSHAKE_END_HZ)){
                Log.d("ListenTone","end");

                short[] chunk = extract_packet(packet);
                StringData = decodeBitChunk(chunk);

            }
            else if(in_packet){
                packet.add(dom);
            }
            else if(match(dom, HANDSHAKE_START_HZ)){
                Log.e("ListenTone - START","start");
                in_packet = true;
            }
        }
        this.mAudioRecord.stop();
    }

    private String decodeBitChunk(short[] chunk) {
        String allData = "";
        for (int i =0; i< chunk.length/2;i++){
            allData+=String.valueOf((char)((chunk[i*2]<<4)+chunk[i*2+1]));
        }
        return allData;
    }

    private boolean match(double freq1, double freq2){
        return (freq1-freq2)<30.0 && (freq1-freq2)>(-30.0);
    }

    private short[] extract_packet(ArrayList<Double> packet) {
        Double[] curPacket = packet.toArray(new Double[packet.size()]); // packet의 값을 그대로 curPacket 리스트에 넣기
        ArrayList<Double> samplingPacket = new ArrayList<>();
        Double[] sampling = new Double[packet.size()/2+1]; // 기존 packet의 길이에서 크기를 반으로 줄임
        // 2를 곱했던 이유는 입력으로 들어오는 소리의 길이가 일정하지 않으며, 소음 또한 추가될 수 있기에 충분하게 받은 후, 절반으로 줄여 출력하는 것.
        // 반으로 강제로 줄이기 때문에 정확도가 좋지 못함. (해당 코드의 경우 수정해도 상관없으며, 그대로 사용해도 무방)

        Log.d("Listentone curSize", Integer.toString(packet.size()));

        for(int i = 0; i < sampling.length; i++) {
            sampling[i] = 0D;
        }

        Log.d("ListenTone Sample Start","start");
        for (int i = 0;i<curPacket.length;i+=2){
            sampling[i/2]= curPacket[i];
        }
        /****************************************

         ------------INPUT YOUR CODE------------- sampling -> samplingPacket (curPacket -> sampling)
         ex) sampling[i/2]= curPacket[i] -> i += 2로 수신한 소리 중 절반만 수용

         ****************************************/

        for(int i = 0; i < sampling.length; i++){
            System.out.println(sampling[i]);
            if (samplingPacket.isEmpty()){
                samplingPacket.add(sampling[i]);
            }else{
                if(samplingPacket.get(samplingPacket.size()-1) != sampling[i]) {samplingPacket.add(sampling[i]);}
            }
        }



        for(int i = 0; i < samplingPacket.size(); i++) {
            Log.d("ListenTone relay", Double.toString(samplingPacket.get(i)));
        }
        short[] chunks = new short[samplingPacket.size()];

        Log.d("ListenTone chunks Start","start");

        int chunkIdx = 0;

        for (chunkIdx=0;chunkIdx<chunks.length;chunkIdx++){
            chunks[chunkIdx]=(short)((samplingPacket.get(chunkIdx)-START_HZ)/STEP_HZ);
        }

        for(int i = 0; i < chunkIdx; i++) {
            Log.d("ListenTone chunks", Short.toString(chunks[i]));
        }
        return chunks;
    }

    // https://github.com/numpy/numpy/blob/v1.17.0/numpy/fft/helper.py#L126-L172
    // 반환하는 모양은 아래와 같다. (n == len, d == duration)
    // f = [0, 1, ...,   n/2-1,     -n/2, ..., -1] / (d*n)   if n is even
    // f = [0, 1, ..., (n-1)/2, -(n-1)/2, ..., -1] / (d*n)   if n is odd
    private Double[] fft(int len, int duration) {
        // val과 results는 참고용 (수정해서 구현해도 상관없음)
        Double val = 1.0 / (len * duration);
        Double[] results = new Double[len];
        if (len%2==0){
            for (int i = 0;i<=len/2-1;i++){
                results[i]=val*i;
            }
            for (int i = len/2;i<len;i++){
                results[i]=(i-len)*val;
            }
        }
        else{
            for (int i = 0;i<=(len-1)/2;i++){
                results[i]=val*i;
            }
            for (int i = (len-1)/2+1;i<len;i++){
                results[i]=(i-len)*val;
            }

        }

        return results;
    }

    private double dominant(double[] transform_chunk) {
        int len = transform_chunk.length;
        double realNum;
        double imgNum;
        double[] complex_val = new double[len];

        Complex[] complex = transform.transform(transform_chunk, TransformType.FORWARD);
        Double[] freq = this.fft(complex.length, 1);

        for(int i = 0; i< complex.length; i++) {
            realNum = complex[i].getReal();
            imgNum = complex[i].getImaginary();
            complex_val[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));
        }

        Double max = 0D;
        int maxIndex = 0;
        for (int i = 0;i<complex.length;i++){
            if (complex[i].abs()>max){
                max = complex[i].abs();
                maxIndex=i;
            }
        }

        /*************************************

         ---------maxIndex를 찾는 과정--------
         pied_piper의 decode.py 파일의 dominant함수 참고
         -> peak_coeff = np.argmax(np.abs(w))
            peak_freq = freqs[peak_coeff]
            return abs(peak_freq * frame_rate)

         *************************************/
        return Math.abs(freq[maxIndex]*mSampleRate); // 0D를 알맞게 고칠 것!
    }
}
