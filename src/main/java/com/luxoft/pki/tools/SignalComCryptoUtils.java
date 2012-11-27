package com.luxoft.pki.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.x500.X500Principal;

import ru.signalcom.crypto.cms.Attribute;
import ru.signalcom.crypto.cms.AttributeType;
import ru.signalcom.crypto.cms.CMSException;
import ru.signalcom.crypto.cms.ContentInfoParser;
import ru.signalcom.crypto.cms.ContentType;
import ru.signalcom.crypto.cms.CounterSignature;
import ru.signalcom.crypto.cms.CounterSignatureGenerator;
import ru.signalcom.crypto.cms.EnvelopedDataGenerator;
import ru.signalcom.crypto.cms.EnvelopedDataParser;
import ru.signalcom.crypto.cms.Recipient;
import ru.signalcom.crypto.cms.RecipientInfo;
import ru.signalcom.crypto.cms.SignedDataGenerator;
import ru.signalcom.crypto.cms.SignedDataParser;
import ru.signalcom.crypto.cms.SignedDataReplacer;
import ru.signalcom.crypto.cms.Signer;
import ru.signalcom.crypto.cms.SignerInfo;

/**
 * Класс по мотивам примера официальной поставки.
 * Адаптирован: Igor Konovalov - Luxoft (C) - 2012.
 * Примеры использования классов, реализующих протокол CMS (RFC 5652). (От себя: да ладно уж, знаем мы ваше RFC...)
 * Copyright (C) 2010 ЗАО "Сигнал-КОМ".
 */
public final class SignalComCryptoUtils implements CryptoUtils {
    
    private static String STORE_TYPE = "PKCS12";
    private static String CRYPTO_PROVIDER = "SC";
    
    private KeyStore keyStore;
    private final String psePath;
    private final String storeFile;
    private final char[] storePassword;
    private final Set<TrustAnchor> trust = new HashSet<TrustAnchor>();
    private final List<Signer> signers = new ArrayList<Signer>();
    private final List<Recipient> recipients = new ArrayList<Recipient>();
    
    /**
     * Список содержит сертификаты, у которых есть закрытые ключи. 
     */
    private final List<CertStore> certStores = new ArrayList<CertStore>(); /* Зачем сюда кидать списки из CRL? */
    private SecureRandom random;
    
    private static Logger LOG = Logger.getLogger(SignalComCryptoUtils.class.getName());
    
    /**
     * 
     * @param keystoreFile - путь до pfx или p12 файла-хранилища. На уровне файла должны распологаться файлы генератора случайных чисел.
     * @param password - пароль к хранилищу (или null, если пароль не задан).
     * @throws Exception
     */
    public SignalComCryptoUtils(final String keystoreFile, final String password) throws Exception {
    	if (keystoreFile == null){
    		throw new NullPointerException("Path to keystore is null"); 
    	}
    	storeFile = keystoreFile;
    	storePassword = password != null ? password.toCharArray() : null;
    	psePath = (new File(storeFile)).getParent() + ";NonInteractive" + (storePassword != null ? ";Password="+password : "");
    	if (LOG.isLoggable(Level.FINE)) {
    		LOG.fine("PSE_PATH: " + psePath);
    	}
    	init();
	}
    
    public SignalComCryptoUtils(final String keystoreFile) throws Exception {
    	this(keystoreFile, null);
    }
    
    /**
     * Установка списка Signers. (Все что было в списке до этого будет стерто)
     * @param signerAliases алиасы ключей участвующих в подписании сообщения
     * @return this (SignalComCryptoUtils)
     * @throws KeyStoreException
     * @throws UnrecoverableKeyException
     * @throws NoSuchAlgorithmException
     */
    public SignalComCryptoUtils signer(String... signerAliases) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
    	signers.clear();
    	if (signerAliases == null) { // ничего не делаем просто очищаем список
    		return this;
    	}
    	for (String signer : signerAliases) {
    		if (keyStore.isKeyEntry(signer)) {
    			addSignerToList(signer);
    		} else {
    			LOG.warning("Alias " + signer + " doesn't have private key and can't be a signer");
    		}
    	}
    	return this;
    }
    
    /**
     * Установка списка получателей.
     * @param recipientsAliases - список алиасов сертификатов получателей (keyEncipherment or keyAgreement bit required)
     * @return this (SignalComCryptoUtils)
     * @throws KeyStoreException
     */
    public SignalComCryptoUtils recipients(String... recipientsAliases) throws KeyStoreException {
    	recipients.clear();
    	if (recipientsAliases != null) {	
	    	for (String recipient : recipientsAliases) {
	    		addRecipientToList(recipient);
	    	}
    	}
    	return this;
    }

	
    /**
     * Инициализация: чтение ключей, сертификатов и т.д.
     * @throws Exception
     */
    private void init() throws Exception {
    	LOG.fine("RNG initialization...");
        random = SecureRandom.getInstance("GOST28147PRNG", "SC");
        random.setSeed(psePath.getBytes());

        LOG.fine("Key store loading...");
        keyStore = KeyStore.getInstance(STORE_TYPE, CRYPTO_PROVIDER);
        InputStream in = new FileInputStream(new File(storeFile));
        keyStore.load(in, storePassword);
        in.close();

        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            System.out.println("I got alias: " + alias);
            if (keyStore.isCertificateEntry(alias)) { // Все сертификаты не в цепочках ключей добавляем в trusted
                X509Certificate cert = getCertificateFromStore(alias);
                trust.add(new TrustAnchor(cert, null));
                System.out.println("\talias '" + alias + "' moves to trusted ");
            } else if (keyStore.isKeyEntry(alias)) {
            	X509Certificate cert = getCertificateFromStore(alias);
            	System.out.println("\talias '" + alias + "' has key");
                certs.add(cert);
            }
        }
        certStores.add(CertStore.getInstance("Collection", new CollectionCertStoreParameters(certs)));
    }

    /**
     * Добавление Signer-а в список подписчиков.
     * @param alias
     * @return Signer's X509Certificate 
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws UnrecoverableKeyException
     */
	private X509Certificate addSignerToList(String alias) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
		PrivateKey priv = (PrivateKey) keyStore.getKey(alias, storePassword);
		X509Certificate cert = getCertificateFromStore(alias);
		signers.add(new Signer(priv, cert, random));
		return cert;
	}
	
	private X509Certificate addRecipientToList(String recipient) throws KeyStoreException {
		X509Certificate cert = getCertificateFromStore(recipient);
		recipients.add(new Recipient(cert));
		return cert;
	}

	private X509Certificate getCertificateFromStore(String alias) throws KeyStoreException {
		return (X509Certificate) keyStore.getCertificate(alias);
	}

    /**
     * Пример формирования подписанного (SignedData) сообщения.
     * @param data подписываемые данные.
     * @param type идентификатор типа подписываемых данных.
     * @param detached если true, то формируется отсоединённая подпись.
     * @return подписанное сообщение.
     * @throws Exception
     */
    private byte[] sign(byte[] data, String type, boolean detached) throws Exception {
        LOG.fine("Signing...");
        InputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        SignedDataGenerator generator = new SignedDataGenerator(out);
        generator.setContentType(type);
        generator.addSigners(signers);
        generator.setDetached(detached);

        OutputStream sigOut = generator.open();

        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            sigOut.write(buf, 0, len);
        }
        generator.close();
        in.close();
        return out.toByteArray();
    }

    public byte[] signAttached(byte[] data) throws Exception {
        return sign(data, ContentType.DATA, false);
    }

    private byte[] sign(byte[] data, boolean detached) throws Exception {
        return sign(data, ContentType.DATA, detached);
    }

    /**
     * Пример формирования удостоверяющей подписи (countersignature)
     * для всех подписей, содержащихся в подписанном (SignedData) сообщении.
     * @param signed подписанное сообщение.
     * @return новое подписанное сообщение.
     * @throws Exception
     */
    private byte[] countersign(byte[] signed) throws Exception {

        System.out.println("Countersigning...");
        InputStream in = new ByteArrayInputStream(signed);
        ContentInfoParser cinfoParser = ContentInfoParser.getInstance(in);
        if (!(cinfoParser instanceof SignedDataParser)) {
            throw new RuntimeException("SignedData expected here");
        }
        SignedDataParser parser = (SignedDataParser) cinfoParser;
        parser.process(false);
        @SuppressWarnings("unchecked")
        Collection<SignerInfo> signerInfos = parser.getSignerInfos();
        Iterator<SignerInfo> it = signerInfos.iterator();
        while (it.hasNext()) {
            SignerInfo signerInfo = it.next();
            Iterator<Signer> it2 = signers.iterator();
            while (it2.hasNext()) {
                Signer signer = it2.next();
                CounterSignatureGenerator gen = new CounterSignatureGenerator(signerInfo);
                CounterSignature csig = gen.generate(signer);
                signerInfo.addUnsignedAttribute(
                        new Attribute(AttributeType.COUNTER_SIGNATURE, csig.getEncoded()));
            }
        }
        parser.close();

        in.reset();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SignedDataReplacer replacer = new SignedDataReplacer(in, out);
        replacer.setSignerInfos(signerInfos);
        replacer.open();
        replacer.process();
        replacer.close();
        in.close();
        return out.toByteArray();
    }

    /**
     * Поиск сертификата.
     * @param stores хранилища сертификатов.
     * @param issuer имя издателя.
     * @param serial серийный номер.
     * @return сертификат.
     * @throws CertStoreException
     */
    private X509Certificate lookupCertificate(List<CertStore> stores, X500Principal issuer, BigInteger serial) throws CertStoreException {

        X509CertSelector csel = new X509CertSelector();
        csel.setIssuer(issuer);
        csel.setSerialNumber(serial);

        Iterator<CertStore> it = stores.iterator();
        while (it.hasNext()) {
            CertStore store = it.next();
            Collection col = store.getCertificates(csel);
            if (!col.isEmpty()) {
                return (X509Certificate) col.iterator().next();
            }
        }
        throw new CertStoreException("certificate not found");
    }

    /**
     * Пример проверки сертификата.
     * @param cert сертификат.
     * @param trust список доверенных сертификатов.
     * @param stores хранилища сертификатов и списков отозванных сертификатов.
     * @return результат проверки.
     * @throws Exception
     */
    private PKIXCertPathBuilderResult verifyCertificate(X509Certificate cert, Set<TrustAnchor> trust, List<CertStore> stores) throws Exception {

    	LOG.fine("X.509 certificate verifying...");
    	LOG.fine("Subject: " + cert.getSubjectX500Principal());
    	
    	System.out.println("Trusted cnt: " + trust.size());
    	System.out.println("Stores: " + stores.size());
    	
        X509CertSelector csel = new X509CertSelector();      
        csel.setCertificate(cert);

        PKIXParameters params = new PKIXBuilderParameters(trust, csel);
        params.setCertStores(stores);
        params.setRevocationEnabled(true);

        CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX", "SC");
        PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult) cpb.build(params);
        LOG.fine("Trust anchor: " + result.getTrustAnchor().getTrustedCert().getIssuerX500Principal());
        return result;
    }

    /**
     * Пример проверки блока подписи (SignerInfo).
     * Включает:
     * <br>1) проверку подписи для данных;</br>
     * <br>2) проверку сертификата;</br>
     * <br>3) проверку удостоверяющих подписей (если есть).</br>
     * @param signerInfo блок подписи.
     * @param trust список доверенных сертификатов.
     * @param stores хранилища сертификатов и списков отозванных сертификатов.
     * @throws Exception
     */
    private void verifySignerInfo(SignerInfo signerInfo, Set<TrustAnchor> trust, List<CertStore> stores) throws Exception {

        X509Certificate cert = lookupCertificate(stores, signerInfo.getIssuer(), signerInfo.getSerialNumber());
        
        if (LOG.isLoggable(Level.FINE)) {
        	LOG.fine("Signature " + cert.getSubjectDN().getName() + " verifying...");
        }
        
        if (!signerInfo.verify(cert)) {
            throw new CMSException("Signature " + cert.getSubjectDN().getName() +" failure");
        }

        //verifyCertificate(cert, trust, stores);
        System.out.println("-------------------------------------\n\n");
        CertificateVerifier.verifyCertificate(cert, keyStore, true, "SC");
        System.out.println("\n\n-------------------------------------");
        

        @SuppressWarnings("unchecked")
        Collection<Attribute> attrs = signerInfo.getUnsignedAttributes();
        if (!attrs.isEmpty()) {
            Iterator<Attribute> it2 = attrs.iterator();
            while (it2.hasNext()) {
                Attribute at = it2.next();

                if (at.getType().equals(AttributeType.COUNTER_SIGNATURE)) {
                	LOG.fine("Countersignature was found...");
                    CounterSignature counterSignature = new CounterSignature(at.getValue(), signerInfo);
                    verifySignerInfo(counterSignature, trust, stores);
                }
            }
        }
    }

    /**
     * Пример проверки подписанного (SignedData) сообщения.
     * @param signed подписанное сообщение.
     * @param data данные, используемые при проверке отсоединённой подписи.
     * @throws Exception
     */
    private void verify(byte[] signed, byte[] data) throws Exception {

    	LOG.fine("Signature(s) verifying...");
        InputStream in = new ByteArrayInputStream(signed);
        ContentInfoParser cinfoParser = ContentInfoParser.getInstance(in);
        if (!(cinfoParser instanceof SignedDataParser)) {
            throw new RuntimeException("SignedData expected here");
        }
        SignedDataParser parser = (SignedDataParser) cinfoParser;
        InputStream content = parser.getContent();
        if (content == null) {
            // отсоединённая подпись
            if (data == null) {
                throw new RuntimeException("detached signed data required");
            }
            parser.setContent(new ByteArrayInputStream(data));
        }
        parser.process();
        certStores.add(parser.getCertificatesAndCRLs());
        in.close();

        @SuppressWarnings("unchecked")
        Collection<SignerInfo> signerInfos = parser.getSignerInfos();
        Iterator<SignerInfo> it = signerInfos.iterator();
        while (it.hasNext()) {
            SignerInfo signerInfo = it.next();
            verifySignerInfo(signerInfo, trust, certStores);
        }
        parser.close();
    }

    public void verify(byte[] signed) throws Exception {
        verify(signed, null);
    }

    /**
     * Пример отделения подписанных данных от подписей.
     * @param signed подписанное сообщение.
     * @return подписанные данные.
     * @throws Exception
     */
    public byte[] detach(byte[] signed) throws Exception {

    	LOG.fine("Data detaching...");
        SignedDataParser parser = new SignedDataParser(new ByteArrayInputStream(signed));
        InputStream content = parser.getContent(false);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int len;
        while ((len = content.read(data)) >= 0) {
            bOut.write(data, 0, len);
        }
        parser.close();
        return bOut.toByteArray();
    }

    /**
     * Пример формирования зашифрованного (EnvelopedData) сообщения.
     * @param plain открытые данные.
     * @return зашифрованное сообщение.
     * @throws Exception
     */
    public byte[] encrypt(byte[] plain) throws Exception {

    	LOG.fine("Enciphering...");
        InputStream bIn = new ByteArrayInputStream(plain);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        EnvelopedDataGenerator generator = new EnvelopedDataGenerator(bOut, random);
        generator.addRecipients(recipients);
        OutputStream out = generator.open();

        byte[] data = new byte[1024];
        int len;
        while ((len = bIn.read(data)) >= 0) {
            out.write(data, 0, len);
        }

        generator.close();
        bIn.close();
        return bOut.toByteArray();
    }

    /**
     * Пример расшифрования EnvelopedData сообщения.
     * @param ciphertext зашифрованное сообщение.
     * @return расшифрованные данные.
     * @throws Exception
     */
    public byte[] decrypt(byte[] ciphertext) throws Exception {

    	LOG.fine("Deciphering...");
        InputStream bIn = new ByteArrayInputStream(ciphertext);

        ContentInfoParser cinfoParser = ContentInfoParser.getInstance(bIn);
        if (!(cinfoParser instanceof EnvelopedDataParser)) {
            throw new RuntimeException("EnvelopedData expected here");
        }
        EnvelopedDataParser parser = (EnvelopedDataParser) cinfoParser;
        @SuppressWarnings("unchecked")
		Collection<RecipientInfo> recInfos = parser.getRecipientInfos();
        Iterator<RecipientInfo> it = recInfos.iterator();
        while (it.hasNext()) {
            RecipientInfo recInfo = (RecipientInfo) it.next();
            X509Certificate cert = lookupCertificate(certStores, recInfo.getIssuer(), recInfo.getSerialNumber());
            if (cert != null) {
                PrivateKey priv =
                        (PrivateKey) keyStore.getKey(keyStore.getCertificateAlias(cert), storePassword);
                if (priv != null) {
                    InputStream content = recInfo.getEncryptedContent(priv, random);
                    ByteArrayOutputStream bOut = new ByteArrayOutputStream();

                    byte[] data = new byte[1024];
                    int len;
                    while ((len = content.read(data)) >= 0) {
                        bOut.write(data, 0, len);
                    }

                    parser.close();
                    bIn.close();
                    return bOut.toByteArray();
                }
            }
        }
        throw new RuntimeException("recipient's private key not found");
    }

    /*
    public static void main(String[] args) {

        for (int i = 0; i < args.length; i++) {
            if (args[i].compareToIgnoreCase("-storeFile") == 0) {
                if (i < args.length) {
                    storeFile = args[++i];
                }
            } else if (args[i].compareToIgnoreCase("-storeType") == 0) {
                if (i < args.length) {
                    STORE_TYPE = args[++i];
                }
            } else if (args[i].compareToIgnoreCase("-storeProvider") == 0) {
                if (i < args.length) {
                    CRYPTO_PROVIDER = args[++i];
                }
            } else if (args[i].compareToIgnoreCase("-storePassword") == 0) {
                if (i < args.length) {
                    storePassword = args[++i].toCharArray();
                }
            } else if (args[i].compareToIgnoreCase("-psePath") == 0) {
                if (i < args.length) {
                    psePath = args[++i];
                }
            } else if (args[i].compareToIgnoreCase("-crlsFile") == 0) {
                if (i < args.length) {
                    crlsFile = args[++i];
                }
            }
        }

        try {
            Security.addProvider(new SignalCOMProvider());
            System.out.println(new ProductInfo());

            SignalComCryptoUtils test = new SignalComCryptoUtils();
            test.init();

            byte[] plaintext = "Test message".getBytes();

            // Отсоединённая подпись
            byte[] signed = test.sign(plaintext, true);
            // Проверка отсоединённой подписи
            test.verify(signed, plaintext);

            // Подпись с инкапсуляцией данных
            signed = test.sign(plaintext);
            // Добавление удостоверяющей подписи
            signed = test.countersign(signed);

            // Зашифрование подписанного сообщения
            byte[] enciphered = test.encrypt(signed);
            // Расшифрование подписанного сообщения
            byte[] deciphered = test.decrypt(enciphered);

            // Проверка подписанного сообщения
            test.verify(deciphered);
            // Извлечение данных из подписанного сообщения
            byte[] data = test.detach(deciphered);

            // Проверка соответствия
            if (!Arrays.equals(plaintext, data)) {
                throw new RuntimeException("plaintext and detached data mismatch");
            }

            System.out.println("All tests passed");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Security.removeProvider("SC");
        }
    }
    */
}