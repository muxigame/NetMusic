package net.muxigame.musiccompat.burn;

import java.net.URI;
import java.nio.file.Path;

/** Original computer single-file URI input; all bytes still use local snapshot validation. */
public final class ComputerSourceInput {
    private ComputerSourceInput(){}
    public static Path localFile(String value){
        String input=value.strip();
        if(!input.regionMatches(true,0,"file:",0,5))return null;
        URI uri=URI.create(input);
        if(uri.isOpaque()||uri.getRawAuthority()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)
            throw new IllegalArgumentException("not_local_audio");
        Path path=Path.of(uri).toAbsolutePath().normalize();
        if(path.toString().startsWith("\\\\"))throw new IllegalArgumentException("not_local_audio");
        return path;
    }
}
