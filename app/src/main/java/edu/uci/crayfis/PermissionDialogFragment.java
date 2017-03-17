package edu.uci.crayfis;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 3/17/2017.
 */

@TargetApi(23)
public class PermissionDialogFragment extends DialogFragment {

    public static final int WRITE_SETTINGS_ACTIVITY = 0;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final Activity activity = getActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.permission_error_title);

        if(!(activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            builder.setMessage(R.string.permission_error)
                    .setPositiveButton(R.string.permission_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.requestPermissions(MainActivity.permissions, 0);
                        }
                    })
                    .setNegativeButton(R.string.permission_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    });
        } else {
            builder.setMessage(R.string.write_settings_error)
                    .setPositiveButton(R.string.permission_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                            intent.setData(Uri.parse("package:" +activity.getPackageName()))
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivityForResult(intent, WRITE_SETTINGS_ACTIVITY);
                        }
                    })
                    .setNegativeButton(R.string.permission_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    });
        }
        return builder.create();

    }

}
