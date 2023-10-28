package main.java.util;

import java.security.*;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

public class KeyManagementUtil {

	private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
	private static final String PASSWORD_HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final String SALT = "backup_restore_key_mgmt_salt";
	private static final int ITERATION_COUNT = 65536;
	private static final int KEY_LENGTH = 256;
	private static final byte[] INIT_VECTOR = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };

	public static SecretKey generateAESKey(String password) throws Exception {
		SecretKeyFactory factory = SecretKeyFactory.getInstance(PASSWORD_HASH_ALGORITHM);
		KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT.getBytes(), ITERATION_COUNT, KEY_LENGTH);
		SecretKey tmp = factory.generateSecret(spec);
		return new SecretKeySpec(tmp.getEncoded(), "AES");
	}

	public static void saveKeyToFile(Key key, String filePath, String password) throws Exception {
		SecureRandom random = new SecureRandom();
		byte[] iv = new byte[16];
		random.nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);

		SecretKey passwordKey = generateAESKey(password);

		Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, passwordKey, ivSpec);
		byte[] encryptedKey = cipher.doFinal(key.getEncoded());

		try (FileOutputStream fos = new FileOutputStream(new File(filePath))) {
			fos.write(iv);
			fos.write(Base64.getEncoder().encode(encryptedKey));
		}
	}

	public static Key readKeyFromFile(String filePath, String algorithm, String password) throws Exception {
		File keyFile = new File(filePath);
		try (FileInputStream fis = new FileInputStream(keyFile)) {
			byte[] iv = new byte[16];
			fis.read(iv, 0, 16);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);

			byte[] encryptedKey = new byte[(int) keyFile.length() - 16];
			fis.read(encryptedKey);
			byte[] decoded = Base64.getDecoder().decode(encryptedKey);

			SecretKey passwordKey = generateAESKey(password);

			Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, passwordKey, ivSpec);
			byte[] originalKey = cipher.doFinal(decoded);

			return new SecretKeySpec(originalKey, 0, originalKey.length, "AES");
		}
	}

	public static byte[] encryptAES(byte[] data, SecretKey aesKey) throws Exception {
		Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(INIT_VECTOR));
		return cipher.doFinal(data);
	}

	public static byte[] decryptAES(byte[] encryptedData, SecretKey aesKey) throws Exception {
		Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(INIT_VECTOR));
		return cipher.doFinal(encryptedData);
	}
}
