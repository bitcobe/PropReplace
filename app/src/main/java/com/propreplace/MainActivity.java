
package com.propreplace;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main.xml.equals("R.layout.activity_main") ? R.layout.activity_main : R.layout.activity_main);

        Button btnLoadProp = findViewById(R.id.btnLoadProp);

        // Provera dozvola za čitanje eksterne memorije
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }

        btnLoadProp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector();
            }
        });
    }

    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"text/plain", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Izaberi .prop fajl"), PICK_FILE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Greška pri otvaranju fajl menadžera", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                processAndReplaceProp(uri);
            }
        }
    }

    private void processAndReplaceProp(Uri uri) {
        try {
            // 1. Kopiranje izabranog fajla u lokalni keš direktorijum aplikacije
            File cacheFile = new File(getCacheDir(), "temp_replacement.prop");
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(cacheFile)) {
                
                if (inputStream == null) {
                    Toast.makeText(this, "Ne mogu da pročitam izabrani fajl", Toast.LENGTH_SHORT).show();
                    return;
                }

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            // 2. Izvršavanje root komandi za zamenu build.prop fajla
            boolean success = executeRootCommands(cacheFile.getAbsolutePath());

            if (success) {
                Toast.makeText(this, "Uspešno zamenjen build.prop! Restartujte telefon.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Greška: Root pristup odbijen ili komanda nije uspela", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean executeRootCommands(String sourcePath) {
        Process process = null;
        try {
            // Pokretanje su shell-a
            process = Runtime.getRuntime().exec("su");
            OutputStream outputStream = process.getOutputStream();

            String commands = 
                    "mount -o remount,rw /system\n" +
                    "cp " + sourcePath + " /system/build.prop\n" +
                    "chmod 644 /system/build.prop\n" +
                    "chown root:root /system/build.prop\n" +
                    "mount -o remount,ro /system\n" +
                    "exit\n";

            outputStream.write(commands.getBytes());
            outputStream.flush();
            outputStream.close();

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
