package com.poulton.rvapp;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;

/** Shared brand colours, surfaces and interaction states. */
final class AdventureStyle {
    static final int INK=0xff173766, GREEN=0xff23754b, GOLD=0xffb05b0c;
    static final int PAPER=0xfff1f5fc, CARD=0xffffffff, TEXT=0xff26354b;
    static final int MUTED=0xff57677e, BORDER=0xffd7e2f2, SOFT=0xffe3ecff, DANGER=0xffac2639;
    static final Typeface DISPLAY=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    static final Typeface BODY=Typeface.create("sans-serif",Typeface.NORMAL);
    private AdventureStyle() {}
    static int accent(Module module) {
        if(module==null)return 0xff285bc1;
        switch(module){
            case TRIPS:case MILEAGE:return 0xff2459bb;
            case PLANS:return 0xff6940bb;
            case PLACES:case ROUTES:return 0xff007b83;
            case CHECKS:case TRIP_CHECKS:return 0xff23754b;
            case ARRIVAL:return 0xff00786c;
            case FUEL:return 0xffa64b0c;
            case EXPENSES:return 0xffa42e6b;
            case MAINTENANCE:return 0xffb03d32;
            case REMINDERS:return 0xffa12c50;
            case CAMPSITES:return 0xff397332;
            case PHOTOS:return 0xff813eae;
            default:return 0xff4655a6;
        }
    }
    static int tint(Module module){return mix(accent(module),CARD,.90f);}
    private static int mix(int a,int b,float fraction){return android.graphics.Color.rgb(
        Math.round(android.graphics.Color.red(a)*(1-fraction)+android.graphics.Color.red(b)*fraction),
        Math.round(android.graphics.Color.green(a)*(1-fraction)+android.graphics.Color.green(b)*fraction),
        Math.round(android.graphics.Color.blue(a)*(1-fraction)+android.graphics.Color.blue(b)*fraction));}
    static Drawable vibrant(Context c,Module module){
        int color=accent(module);GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{color,mix(color,0xff10244b,.22f)});bg.setCornerRadius(16*c.getResources().getDisplayMetrics().density);
        return new RippleDrawable(ColorStateList.valueOf(0x35ffffff),bg,null);
    }
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
        return new RippleDrawable(ColorStateList.valueOf(0x30285bc1),panel(c,fill,border),null);
    }
}
