package com.aiscam.antiscam.media;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Mp3FramesTest {
    @Test void stripsFileTagsAndSeekMetadataBeforeJoining() {
        byte[] metadata=QwenTtsClientTest.frames(1);
        System.arraycopy("Info".getBytes(StandardCharsets.US_ASCII),0,metadata,21,4);
        var bytes=new ByteArrayOutputStream();
        bytes.writeBytes(new byte[]{'I','D','3',4,0,0,0,0,0,3,1,2,3});
        bytes.writeBytes(metadata);bytes.writeBytes(QwenTtsClientTest.frames(3));
        byte[] tail=new byte[128];tail[0]='T';tail[1]='A';tail[2]='G';bytes.writeBytes(tail);
        var part=Mp3Frames.read(bytes.toByteArray());
        assertArrayEquals(QwenTtsClientTest.frames(3),part.frames());
        assertEquals(3456,part.samples());assertEquals(44100,part.sampleRate());assertEquals(1,part.channels());
        assertThrows(TtsClient.TtsException.class,()->Mp3Frames.read(metadata));
    }

    @Test void readsMpeg2AtRequested22050HzAndRejectsMixedFormats() {
        byte[] frames=new byte[52*3];
        for(int i=0;i<3;i++){frames[i*52]=(byte)255;frames[i*52+1]=(byte)243;frames[i*52+2]=32;frames[i*52+3]=(byte)192;}
        var part=Mp3Frames.read(frames);
        assertEquals(22050,part.sampleRate());assertEquals(1728,part.samples());assertArrayEquals(frames,part.frames());
        var mixed=new ByteArrayOutputStream();mixed.writeBytes(frames);mixed.writeBytes(QwenTtsClientTest.frames(1));
        assertThrows(TtsClient.TtsException.class,()->Mp3Frames.read(mixed.toByteArray()));
        assertThrows(TtsClient.TtsException.class,()->Mp3Frames.read(new byte[]{'I','D','3'}));
    }

    @Test void mismatchedSentenceDurationsCannotProducePublishedAudio() {
        assertThrows(TtsClient.TtsException.class,()->TtsClient.assemble(QwenTtsClientTest.frames(3),
                SentenceSegmenter.split("你好，下一句。"),java.util.List.of(78L)));
    }
}
