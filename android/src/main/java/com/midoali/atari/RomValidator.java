package com.midoali.atari;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;

final class RomValidator {
    private RomValidator() {}
    static boolean isGenesis(File file) throws IOException {
        if (!file.isFile() || file.length() < 0x190 || file.length() > 16*1024*1024) return false;
        byte[] header = new byte[16];
        try (FileInputStream stream = new FileInputStream(file)) {
            if (stream.skip(0x100) != 0x100 || stream.read(header) != header.length) return false;
        }
        String text = new String(header, java.nio.charset.StandardCharsets.US_ASCII);
        return text.startsWith("SEGA");
    }
    static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileInputStream stream=new FileInputStream(file)){
            byte[] buffer=new byte[65536];int count;
            while((count=stream.read(buffer))!=-1)digest.update(buffer,0,count);
        }
        StringBuilder result=new StringBuilder();
        for(byte value:digest.digest())result.append(String.format("%02x",value&255));
        return result.toString();
    }
    static boolean sameBytes(File first,File second)throws IOException {
        if(!second.isFile()||first.length()!=second.length())return false;
        try(FileInputStream a=new FileInputStream(first);FileInputStream b=new FileInputStream(second)){
            byte[] x=new byte[65536],y=new byte[65536];int count;
            while((count=a.read(x))!=-1){int total=0;while(total<count){int read=b.read(y,total,count-total);if(read<0)return false;total+=read;}if(!Arrays.equals(x,y))return false;}
            return true;
        }
    }
}
