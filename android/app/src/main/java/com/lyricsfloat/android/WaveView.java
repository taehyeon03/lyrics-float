package com.lyricsfloat.android;
import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Timeline in the desktop style: track, progress, white thumb, and an optional decorative wave over the played part. It does not read device audio. */
public final class WaveView extends View {
    interface SeekListener{void onSeek(float ratio);void onScrub(float ratio);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final TimeAnimator timer;private final int[] stops=new int[3];private final float[] at={0f,.55f,1f};
    private float phase=0,progress=0,dragRatio=-1;private boolean playing=false,wave=false,seekable=false;private SeekListener listener;
    public WaveView(Context c){super(c);line.setStrokeCap(Paint.Cap.ROUND);timer=new TimeAnimator();timer.setTimeListener((a,total,delta)->{phase+=delta/1000f*2f;invalidate();});}
    public void setSeekListener(SeekListener l){listener=l;}
    public void setWave(boolean on){wave=on;animateIfNeeded();invalidate();}
    public void setSeekable(boolean on){seekable=on;}
    public boolean isDragging(){return dragRatio>=0;}
    public void updatePlayback(boolean on,float ratio){playing=on;progress=Math.max(0,Math.min(1,ratio));animateIfNeeded();invalidate();}
    private void animateIfNeeded(){if(wave&&playing&&isShown()&&isAttachedToWindow()){if(!timer.isStarted())timer.start();}else timer.cancel();}
    @Override protected void onDetachedFromWindow(){timer.cancel();super.onDetachedFromWindow();}
    private float ratioAt(float x){float pad=getHeight()*.18f;return Math.max(0,Math.min(1,(x-pad)/(getWidth()-2*pad)));}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!seekable||listener==null)return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:getParent().requestDisallowInterceptTouchEvent(true);
            case MotionEvent.ACTION_MOVE:dragRatio=ratioAt(e.getX());listener.onScrub(dragRatio);invalidate();return true;
            case MotionEvent.ACTION_UP:listener.onSeek(ratioAt(e.getX()));dragRatio=-1;invalidate();return true;
            case MotionEvent.ACTION_CANCEL:dragRatio=-1;invalidate();return true;
        }
        return false;
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        float h=getHeight(),pad=h*.18f,left=pad,right=getWidth()-pad,base=h*.78f,shown=dragRatio>=0?dragRatio:progress,x=left+(right-left)*shown,stroke=h*.07f;
        line.setStrokeWidth(stroke);line.setColor(0xff696671);canvas.drawLine(left,base,right,base,line);
        if(wave&&x-left>1){float width=x-left,height=base-h*.06f;
            for(int layer=0;layer<3;layer++){path.reset();path.moveTo(left,base);float amplitude=height*(.68f+.22f*(float)Math.sin(phase*.9f+layer*.9f));for(int i=1;i<40;i++){float t=i/40f;float envelope=(float)Math.pow(Math.sin(Math.PI*t),1.1);float v=.58f+.4f*(float)Math.sin(t*Math.PI*(2+layer)+phase*(layer==1?-1:1)+layer*1.6);path.lineTo(left+width*t,base-amplitude*envelope*v);}path.lineTo(x,base);path.close();
                stops[0]=Color.argb(40+layer*18,112,153,255);stops[1]=Color.argb(65+layer*20,207,184,255);stops[2]=Color.argb(100+layer*20,255,232,249);paint.setShader(new LinearGradient(left,0,x,0,stops,at,Shader.TileMode.CLAMP));canvas.drawPath(path,paint);}
            paint.setShader(null);}
        line.setColor(0xffd9d7f8);canvas.drawLine(left,base,x,base,line);
        paint.setColor(0xff625578);canvas.drawCircle(x,base,pad*.55f,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke*1.15f);paint.setColor(0xffffffff);canvas.drawCircle(x,base,pad*.55f,paint);paint.setStyle(Paint.Style.FILL);
    }
}
