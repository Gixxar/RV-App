package com.poulton.rvapp;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;

/** Shared brand colours, surfaces and interaction states. */
final class AdventureStyle {
    static final int INK=0xff193d33, GREEN=0xff2f6652, GOLD=0xffb49a67;
    static final int PAPER=0xfff6f5f0, CARD=0xffffffff, TEXT=0xff293c34;
    static final int MUTED=0xff65756b, BORDER=0xffe1e6de, SOFT=0xffeaf0e9, DANGER=0xffa63838;
    static final Typeface DISPLAY=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    static final Typeface BODY=Typeface.create("sans-serif",Typeface.NORMAL);
    private AdventureStyle() {}
    static GradientDrawable panel(Context c,int color,int border) {
        float d=c.getResources().getDisplayMetrics().density;
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(16*d);
        if(border!=0)bg.setStroke(Math.max(1,(int)d),border);return bg;
    }
    static Drawable paper(){return new ColorDrawable(PAPER);}
    static Drawable input(Context c){
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},panel(c,CARD,GREEN));
        states.addState(new int[]{},panel(c,CARD,BORDER));return states;
    }
    static Drawable pressable(Context c,int fill,int border){
        return new RippleDrawable(ColorStateList.valueOf(fill==INK?0x30ffffff:0x183f6752),panel(c,fill,border),null);
    }
}
