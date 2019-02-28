package zuwagon.zutracklib;

import android.content.Context;
import android.content.SharedPreferences;

public class SpHelper {

    public static void set_isstarted(Context context, boolean b) {
        try {
            SharedPreferences pref = context.getSharedPreferences("sp", 0); // 0 - for private mode
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean("istarted", b);
            editor.commit();
        } catch (Exception e) {
        }
    }


    public static boolean get_istarted(Context context) {
        try {
            SharedPreferences pref = context.getSharedPreferences("sp", 0); // 0 - for private mode
            return pref.getBoolean("istarted", false);
        } catch (Exception e) {
            return false;
        }
    }


}
