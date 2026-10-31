package com.lyricsfloat.android;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Round chip button with a drawn glyph, so icons never fall back to colored emoji. */
public final class IconButton extends View {
    static final int PREVIOUS=0,PLAY=1,PAUSE=2,NEXT=3,SETTINGS=4,CLOSE=5,MINUS=6,PLUS=7;
    private final Paint chip=new Paint(Paint.ANTI_ALIAS_FLAG),ink=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private int glyph;private boolean strong;
    public IconButton(Context c,int glyph){super(c);this.glyph=glyph;ink.setColor(0xffeae6e9);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);setClickable(true);}
    public void setGlyph(int g){if(g!=glyph){glyph=g;invalidate();}}
    public void setStrong(boolean s){strong=s;invalidate();}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,r=Math.min(w,h)/2,u=r/10;
        chip.setColor(isPressed()?0x37ffffff:strong?0x2effffff:0x1cffffff);canvas.drawCircle(cx,cy,r,chip);
        ink.setStrokeWidth(u*1.6f);path.reset();
        switch(glyph){
            case PLAY:ink.setStyle(Paint.Style.FILL);path.moveTo(cx-u*2.6f,cy-u*3.6f);path.lineTo(cx+u*3.8f,cy);path.lineTo(cx-u*2.6f,cy+u*3.6f);path.close();canvas.drawPath(path,ink);break;
            case PAUSE:ink.setStyle(Paint.Style.FILL);canvas.drawRoundRect(cx-u*3.2f,cy-u*3.4f,cx-u*1f,cy+u*3.4f,u*.6f,u*.6f,ink);canvas.drawRoundRect(cx+u*1f,cy-u*3.4f,cx+u*3.2f,cy+u*3.4f,u*.6f,u*.6f,ink);break;
            case PREVIOUS:case NEXT:{ink.setStyle(Paint.Style.FILL);float d=glyph==NEXT?1:-1;path.moveTo(cx-d*u*3f,cy-u*3.2f);path.lineTo(cx+d*u*2f,cy);path.lineTo(cx-d*u*3f,cy+u*3.2f);path.close();canvas.drawPath(path,ink);canvas.drawRoundRect(Math.min(cx+d*u*2.2f,cx+d*u*3.6f),cy-u*3.2f,Math.max(cx+d*u*2.2f,cx+d*u*3.6f),cy+u*3.2f,u*.5f,u*.5f,ink);break;}
            case SETTINGS:{ink.setStyle(Paint.Style.STROKE);canvas.drawCircle(cx,cy,u*2.4f,ink);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(cx+(float)Math.cos(a)*u*3.6f,cy+(float)Math.sin(a)*u*3.6f,cx+(float)Math.cos(a)*u*4.6f,cy+(float)Math.sin(a)*u*4.6f,ink);}break;}
            case CLOSE:ink.setStyle(Paint.Style.STROKE);canvas.drawLine(cx-u*3,cy-u*3,cx+u*3,cy+u*3,ink);canvas.drawLine(cx+u*3,cy-u*3,cx-u*3,cy+u*3,ink);break;
            case PLUS:ink.setStyle(Paint.Style.STROKE);canvas.drawLine(cx,cy-u*3.4f,cx,cy+u*3.4f,ink);
            case MINUS:ink.setStyle(Paint.Style.STROKE);canvas.drawLine(cx-u*3.4f,cy,cx+u*3.4f,cy,ink);break;
        }
    }
}
