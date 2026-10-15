package net.muxigame.musiccompat.burn;
import java.nio.file.Path;

public final class ComputerSourceInputTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError();}
    public static void main(String[] args){
        Path expected=Path.of(System.getProperty("java.io.tmpdir"),"own CC0 sample.wav").toAbsolutePath().normalize();
        check(ComputerSourceInput.localFile(expected.toUri().toString()).equals(expected));
        check(ComputerSourceInput.localFile("  "+expected.toUri()+"  ").equals(expected));
        for(String value:new String[]{"1868207326","BV1234567890","https://music.163.com/song?id=1868207326","https://licensed.example/own.wav","\u00a01868207326\u202f","  1868207326  ","https://bad link.invalid/own.wav"})
            check(ComputerSourceInput.localFile(value)==null);
        for(String value:new String[]{"file://remote/share/own.wav","file://user@remote/share/own.wav","file:C:/own.wav",expected.toUri()+"?secret=1",expected.toUri()+"#x"}){
            boolean refused=false;try{ComputerSourceInput.localFile(value);}catch(IllegalArgumentException invalid){refused=true;}check(refused);
        }
        System.out.println("{\"computerInputChecks\":"+checks+",\"userFilesRead\":false,\"cloudCalls\":0,\"mcStarted\":false}");
    }
}
