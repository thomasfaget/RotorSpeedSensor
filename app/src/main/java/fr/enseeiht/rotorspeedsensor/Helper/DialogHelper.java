package fr.enseeiht.rotorspeedsensor.Helper;

import android.app.AlertDialog;
import android.content.Context;

/**
 * @author : Matt
 *         Date de création : 27/06/2017.
 */

public class DialogHelper {
    public static void show(Context context, String message) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setMessage(message);
        alert.show();
    }
}
