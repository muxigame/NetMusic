package net.muxigame.musiccompat.burn;
public final class ComputerControlsTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError();}
    private static boolean intersects(int x,int y,int w,int h,int a,int b,int c,int d){return x<a+c&&x+w>a&&y<b+d&&y+h>b;}
    public static void main(String[] args){
        for(int[] size:new int[][]{{320,240},{480,240},{640,240},{640,360},{800,600},{320,360},{384,256},{1920,1080}}){
            int width=size[0],height=size[1],left=(width-176)/2,top=(height-216)/2;
            var layout=BurnScreenRuntime.computerControls(width,height,left,top,176,216);
            var controls=java.util.List.of(layout.permission(),layout.local(),layout.cancel(),layout.source(),layout.login());
            for(var box:controls){
                check(box.x()>=8&&box.y()>=8&&box.x()+box.width()<=width-8&&box.y()+20<=height-8);
                check(!intersects(box.x(),box.y(),box.width(),20,left,top,176,216));
                var area=layout.occupied();check(box.x()>=area.x()&&box.y()>=area.y()&&box.x()+box.width()<=area.x()+area.width()&&box.y()+20<=area.y()+area.height());
            }
            for(int i=0;i<controls.size();i++)for(int j=i+1;j<controls.size();j++){
                var a=controls.get(i);var b=controls.get(j);check(!intersects(a.x(),a.y(),a.width(),20,b.x(),b.y(),b.width(),20));
            }
            var area=layout.occupied();var tip=BurnScreenRuntime.tooltipBox(width,height,area.x(),area.y(),area.width(),area.height(),184,80);
            if(tip!=null){
                check(!intersects(tip.x(),tip.y(),tip.width(),tip.height(),left,top,176,216));
                for(var box:controls)check(!intersects(tip.x(),tip.y(),tip.width(),tip.height(),box.x(),box.y(),box.width(),20));
            }
        }
        System.out.println("{\"computerLayoutChecks\":"+checks+",\"guiCases\":8,\"mcStarted\":false}");
    }
}
