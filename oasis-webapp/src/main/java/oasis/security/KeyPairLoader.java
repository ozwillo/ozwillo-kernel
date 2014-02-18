package oasis.security;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAMultiPrimePrivateCrtKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyPairLoader {
  private static final Logger logger = LoggerFactory.getLogger(KeyPairLoader.class);
  private static final int KEY_SIZE = 2048;

  public static KeyPair loadOrGenerateKeyPair(@Nullable Path privateKeyPath, @Nullable Path publicKeyPath) {
    boolean storePrivateKey = false;
    boolean storePublicKey = false;

    if (privateKeyPath == null) {
      if (publicKeyPath != null) {
        logger.warn("Private key is not specified. Cannot create a key-pair from a public key.");
      }
      logger.debug("Generating a key-pair in-memory");
      return generateRandomKeyPair();
    }

    KeyPair keyPair = null;
    if (publicKeyPath == null) {
      try {
        logger.debug("Private key configured but no public key given, extracting the public key from the private key at {}", privateKeyPath);
        keyPair = loadFromPrivateKeyFile(privateKeyPath);
      } catch (FileNotFoundException e) {
        logger.warn("Cannot load the key pair from private key file {}.", privateKeyPath, e);
        storePrivateKey = true;
      } catch (KeyException | IOException e) {
        logger.warn("Cannot load the key pair from private key file {}.", privateKeyPath, e);
      }
    } else {
      try {
        logger.debug("Loading key-pair from private key {} and public key {}", privateKeyPath, publicKeyPath);
        keyPair = loadFromFiles(privateKeyPath, publicKeyPath);
      } catch (FileNotFoundException e) {
        logger.warn("Cannot load the key pair from private key file {} and public key file {}.",
            new Object[]{privateKeyPath, publicKeyPath, e});
        storePublicKey = !Files.exists(publicKeyPath);
        storePrivateKey = storePublicKey && !Files.exists(privateKeyPath);
      } catch (KeyException | IOException e) {
        logger.warn("Cannot load the key pair from private key file {} and public key file {}.",
            new Object[]{privateKeyPath, publicKeyPath, e});
      }
    }

    if (keyPair == null) {
      logger.warn("Generating a key-pair in-memory");
      keyPair = generateRandomKeyPair();
    }

    if (storePrivateKey) {
      try {
        logger.debug("Storing the private key at {}.", privateKeyPath);
        storePrivateKey(keyPair.getPrivate(), privateKeyPath);
      } catch (IOException e) {
        logger.warn("Cannot store the private key at {}.", privateKeyPath, e);
        storePublicKey = false;
      }
    }

    if (storePublicKey) {
      try {
        logger.debug("Storing the public key at {}.", publicKeyPath);
        storePublicKey(keyPair.getPublic(), publicKeyPath);
      } catch (IOException e) {
        logger.warn("Cannot store the public key at {}.", publicKeyPath, e);
      }
    }

    return keyPair;
  }

  public static KeyPair generateRandomKeyPair() {
    KeyPairGenerator generator = getKeyPairGenerator();
    return generator.generateKeyPair();
  }

  private static PublicKey extractPublicKeyFromPrivateKey(PrivateKey privateKey) throws KeyException {
    try {
      KeyFactory keyFactory = getKeyFactory();
      if (privateKey instanceof RSAPrivateCrtKey) {
        return keyFactory.generatePublic(
            new RSAPublicKeySpec(((RSAPrivateCrtKey) privateKey).getModulus(), ((RSAPrivateCrtKey) privateKey).getPublicExponent()));
      } else if (privateKey instanceof RSAMultiPrimePrivateCrtKey) {
        return keyFactory.generatePublic(new RSAPublicKeySpec(((RSAMultiPrimePrivateCrtKey) privateKey).getModulus(),
            ((RSAMultiPrimePrivateCrtKey) privateKey).getPublicExponent()));
      } else {
        throw new KeyException();
      }
    } catch (InvalidKeySpecException e) {
      logger.warn("Can't extract a public key from the private key.", e);
      throw new KeyException();
    }
  }

  private static KeyPair loadFromFiles(Path privateKeyPath, Path publicKeyPath) throws IOException, KeyException {
    PrivateKey privateKey = loadPrivateKey(privateKeyPath);
    PublicKey publicKey;
    try {
      publicKey = loadPublicKey(publicKeyPath);
      // TODO: Verify that the public key match with the private key
    } catch (KeyException e) {
      logger.error("Can't extract a public key from the file {}", publicKeyPath, e);
      publicKey = extractPublicKeyFromPrivateKey(privateKey);
    }
    return new KeyPair(publicKey, privateKey);
  }

  private static KeyPair loadFromPrivateKeyFile(Path privateKeyPath) throws IOException, KeyException {
    PrivateKey privateKey = loadPrivateKey(privateKeyPath);
    return new KeyPair(extractPublicKeyFromPrivateKey(privateKey), privateKey);
  }

  private static PrivateKey loadPrivateKey(Path privateKeyPath) throws IOException, KeyException {
    if (!Files.exists(privateKeyPath)) {
      logger.warn("The private key at {} doesn't exist.", privateKeyPath);
      throw new FileNotFoundException(privateKeyPath.toString());
    }
    if (!Files.isRegularFile(privateKeyPath)) {
      logger.warn("The private key at {} is not a file.", privateKeyPath);
      throw new IOException();
    }
    if (!Files.isReadable(privateKeyPath)) {
      logger.warn("The private key at {} exists but is not readable.", privateKeyPath);
      throw new AccessDeniedException(privateKeyPath.toString());
    }

    PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Files.readAllBytes(privateKeyPath));
    KeyFactory factory = getKeyFactory();
    try {
      return factory.generatePrivate(privateKeySpec);
    } catch (InvalidKeySpecException e) {
      logger.warn("Unable to extract the private key from the file {}", privateKeyPath);
      throw new KeyException(e);
    }
  }

  private static PublicKey loadPublicKey(Path publicKeyPath) throws IOException, KeyException {
    if (!Files.exists(publicKeyPath)) {
      logger.warn("The public key at {} doesn't exist.", publicKeyPath);
      throw new FileNotFoundException(publicKeyPath.toString());
    }
    if (!Files.isRegularFile(publicKeyPath)) {
      logger.warn("The public key at {} is not a file.", publicKeyPath);
      throw new IOException();
    }
    if (!Files.isReadable(publicKeyPath)) {
      logger.warn("The public key at {} exists but is not readable.", publicKeyPath);
      throw new AccessDeniedException(publicKeyPath.toString());
    }

    X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Files.readAllBytes(publicKeyPath));
    KeyFactory factory = getKeyFactory();

    try {
      return factory.generatePublic(publicKeySpec);
    } catch (InvalidKeySpecException e) {
      logger.warn("Unable to load the public key from the file {}", publicKeyPath);
      throw new KeyException(e);
    }
  }

  private static void storePrivateKey(PrivateKey privateKey, Path privateKeyPath) throws IOException {
    if (Files.exists(privateKeyPath)) {
      return; // Keys shouldn't be overwritten
    }

    Path pathParent = privateKeyPath.getParent();
    if (!ensureDir(pathParent)) {
      logger.warn("The path {} doesn't exist and cannot be created. Cannot store the private key at this path.", pathParent);
      throw new FileNotFoundException();
    }
    if (!Files.isWritable(pathParent)) {
      logger.warn("You are not allowed to store the private key at the path {}.", privateKeyPath);
      throw new AccessDeniedException(privateKeyPath.toString());
    }

    PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
    Files.write(privateKeyPath, privateKeySpec.getEncoded());
  }

  private static void storePublicKey(PublicKey publicKey, Path publicKeyPath) throws IOException {
    if (Files.exists(publicKeyPath)) {
      return; // Keys shouldn't be overwritten
    }

    Path pathParent = publicKeyPath.getParent();
    if (!ensureDir(pathParent)) {
      logger.warn("The path {} doesn't exist and cannot be created. Cannot store the public key at this path.", pathParent);
      throw new FileNotFoundException();
    }
    if (!Files.isWritable(pathParent)) {
      logger.warn("You are not allowed to store the public key at the path {}.", publicKeyPath);
      throw new AccessDeniedException(publicKeyPath.toString());
    }

    X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
    Files.write(publicKeyPath, publicKeySpec.getEncoded());
  }

  private static KeyFactory getKeyFactory() {
    try {
      return KeyFactory.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot create the KeyFactory. RSA algorithm seems to be unavailable.", e);
    }
  }

  private static KeyPairGenerator getKeyPairGenerator() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(KEY_SIZE);
      return generator;
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot create the KeyPairGenerator. RSA algorithm seems to be unavailable.", e);
    }
  }

  private static boolean ensureDir(Path dir) {
    if (dir == null) {
      return false;
    }
    if (Files.exists(dir)) {
      return Files.isDirectory(dir);
    }

    try {
      Files.createDirectories(dir);
      assert Files.isDirectory(dir);
      return true;
    } catch (IOException e) {
      logger.warn("The directory path {} cannot be created.", dir, e);
    }

    return false;
  }
}
