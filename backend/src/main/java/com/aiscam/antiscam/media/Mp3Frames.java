package com.aiscam.antiscam.media;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Validates Layer III frames and strips per-file metadata before joining sentence audio. */
final class Mp3Frames {
    private static final int[] MPEG1_KBPS = {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320};
    private static final int[] MPEG2_KBPS = {0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
    private Mp3Frames() {}
    record Part(byte[] frames, long samples, int sampleRate, int channels) {}
    static Part read(byte[] bytes) {
        int at=0, rate=0, channels=0;long samples=0;
        var result=new ByteArrayOutputStream();
        if (tag(bytes,0,"ID3")) {
            require(bytes.length>=10);
            int size=0;
            for(int i=6;i<10;i++){require((bytes[i]&128)==0);size=(size<<7)|(bytes[i]&127);}
            at=10+size+((bytes[5]&16)!=0?10:0);require(at<=bytes.length);
        }
        while(at<bytes.length) {
            if(bytes.length-at==128 && tag(bytes,at,"TAG")) break;
            require(at+4<=bytes.length && (bytes[at]&255)==255 && (bytes[at+1]&224)==224);
            int h1=bytes[at+1]&255,h2=bytes[at+2]&255,h3=bytes[at+3]&255;
            int version=(h1>>3)&3,layer=(h1>>1)&3,index=h2>>4,sampleIndex=(h2>>2)&3;
            require(version!=1 && layer==1 && index>0 && index<15 && sampleIndex<3);
            int hz=new int[]{44100,48000,32000}[sampleIndex] / (version==3?1:version==2?2:4);
            int count=version==3?1152:576, ch=(h3>>6)==3?1:2;
            int kbps=(version==3?MPEG1_KBPS:MPEG2_KBPS)[index];
            int length=(version==3?144000:72000)*kbps/hz+((h2>>1)&1);
            require(at+length<=bytes.length);
            if(rate==0){rate=hz;channels=ch;}else require(rate==hz && channels==ch);
            int side=version==3?(ch==1?17:32):(ch==1?9:17);
            int info=at+4+((h1&1)==0?2:0)+side;
            boolean metadata=(info+4<=at+length && (tag(bytes,info,"Xing")||tag(bytes,info,"Info")))
                    || (at+40<=at+length && tag(bytes,at+36,"VBRI"));
            if(!metadata){result.write(bytes,at,length);samples+=count;}
            at+=length;
        }
        require(samples>0);
        return new Part(result.toByteArray(),samples,rate,channels);
    }
    private static boolean tag(byte[] bytes,int at,String value) {
        return at+value.length()<=bytes.length && new String(bytes,at,value.length(),StandardCharsets.US_ASCII).equals(value);
    }
    private static void require(boolean valid) { if(!valid) throw new TtsClient.TtsException("Invalid or unsupported MP3 audio"); }
}
