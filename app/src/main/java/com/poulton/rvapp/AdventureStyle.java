package com.poulton.rvapp;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Poster palette and subtle paper texture, drawn at the device's resolution. */
final class AdventureStyle {
    static final int INK=0xff17272d, GREEN=0xff3e5650, GOLD=0xffad9672;
    static final int PAPER=0xfff5efdf, CARD=0xfffffcf5, TEXT=0xff243034;
    static final Typeface DISPLAY=Typeface.create("sans-serif-condensed",Typeface.BOLD);
    static final Typeface BODY=Typeface.create("sans-serif",Typeface.NORMAL);
    private AdventureStyle() {}
    static GradientDrawable panel(Context c,int color,int border) {
        float d=c.getResources().getDisplayMetrics().density;
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(8*d);
        bg.setStroke(Math.max(1,(int)d),border);return bg;
    }
    static Drawable paper() {
        return new Drawable() {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            Shader shader;
            @Override protected void onBoundsChange(Rect bounds) {
                shader=new LinearGradient(0,0,Math.max(1,bounds.width()),Math.max(1,bounds.height()),new int[]{0xfffaf6eb,PAPER,0xffeee4d1},null,Shader.TileMode.CLAMP);
            }
            @Override public void draw(Canvas canvas) {
                Rect b=getBounds();
                paint.setShader(shader);
                canvas.drawRect(b,paint);paint.setShader(null);
                paint.setColor(0x0ca68c63);
                for(int y=b.top+3;y<b.bottom;y+=13)for(int x=b.left+(y%19);x<b.right;x+=23)canvas.drawCircle(x,y,0.65f,paint);
            }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(ColorFilter filter) { }
            @Override public int getOpacity(){return PixelFormat.OPAQUE;}
        };
    }
    static final class Stripes extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path p=new Path();
        Stripes(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c) {
            float w=getWidth(),h=getHeight();
            paint.setColor(INK);c.drawRect(0,h*.62f,w,h,paint);
            p.reset();p.moveTo(0,0);p.cubicTo(w*.34f,h*.14f,w*.42f,h,w,h*.4f);p.lineTo(w,h);p.lineTo(0,h);p.close();
            paint.setColor(GREEN);c.drawPath(p,paint);
            p.reset();p.moveTo(0,h*.12f);p.cubicTo(w*.32f,h*.2f,w*.43f,h*.92f,w,h*.55f);p.lineTo(w,h*.72f);p.cubicTo(w*.4f,h,w*.3f,h*.46f,0,h*.43f);p.close();
            paint.setColor(GOLD);c.drawPath(p,paint);
        }
    }
}
