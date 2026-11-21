package com.aquaquenchers.bms;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps the offline queue on the device encrypted with AES-256-GCM. The key is created inside the
 * Android Keystore and never leaves it. The file sits in the no-backup folder, so it is not copied
 * by cloud backup or device transfer. If the key or file cannot be read the queue starts empty and
 * the caller tells the user that unsent saves could not be recovered.
 */
final class OfflineStore implements OfflineQueue.Sealer {
    private static final String ALIAS = "aq_offline_queue_v1";
    private final File file;
    OfflineStore(Context context) { file = new File(context.getNoBackupFilesDir(), "offline_queue.bin"); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
            .setUnlockedDeviceRequired(true).build());
        return g.generateKey();
    }
    @Override public byte[] seal(byte[] plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV(), body = c.doFinal(plain);
        return ByteBuffer.allocate(1 + iv.length + body.length).put((byte) iv.length).put(iv).put(body).array();
    }
    @Override public byte[] open(byte[] sealed) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(sealed); byte[] iv = new byte[b.get() & 0xff]; b.get(iv); byte[] body = new byte[b.remaining()]; b.get(body);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return c.doFinal(body);
    }
    OfflineQueue load() throws Exception { return file.exists() ? OfflineQueue.deserialize(Files.readAllBytes(file.toPath()), this) : new OfflineQueue(); }
    void save(OfflineQueue q) throws Exception {
        if (q.size() == 0) { if (file.exists() && !file.delete()) throw new IllegalStateException("Could not clear the offline queue."); return; }
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(q.serialize(this)); out.getFD().sync(); }
        if (!tmp.renameTo(file)) throw new IllegalStateException("Could not store the offline queue.");
    }
}
