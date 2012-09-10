package com.luxoft.pkcs7.cms.tests;

import java.io.ByteArrayInputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import ru.CryptoPro.JCP.params.AlgIdSpec;
import ru.CryptoPro.JCP.params.OID;
import ru.CryptoPro.JCPRequest.GostCertificateRequest;

public class St1CreateCertAndKeys {

	public static final String CONT_NAME_RAPIDA = "Rapida";

	public static final char[] PASSWORD_RAPIDA = "a".toCharArray();

	public static final String DNAME_RAPIDA = "CN=Container_Rapida, O=Rapida, C=RU";
	
	

	public static final String CONT_NAME_TINKOFF = "Tinkoff";

	public static final char[] PASSWORD_TINKOFF = "b".toCharArray();

	public static final String DNAME_TINKOFF = "CN=Container_Tinkoff, O=Tinkoff, C=RU";

	public static void main(String[] args) throws Exception {

		// ������������� �������� ���� ��� � ������ � ���������
		saveKeyWithCert(genKey(Constants.SIGN_KEY_PAIR_ALG + "DH"), CONT_NAME_RAPIDA, PASSWORD_RAPIDA, DNAME_RAPIDA);

		// ������������� �������� ���� ��� � ����������� � ������ � ���������
		//saveKeyWithCert(genKeyWithParams(Constants.EXCH_KEY_PAIR_ALG), CONT_NAME_TINKOFF, PASSWORD_TINKOFF, DNAME_TINKOFF);
		saveKeyWithCert(genKey(Constants.SIGN_KEY_PAIR_ALG + "DH"), CONT_NAME_TINKOFF, PASSWORD_TINKOFF, DNAME_TINKOFF);

		// �������� ����������� ��������� ��� ������ �����
		final KeyStore hdImageStore = KeyStore.getInstance(Constants.KEYSTORE_TYPE);
		// �������� ����������� �������� (��������������, ��� �� ����������
		// ��������� ���������� ������������)
		hdImageStore.load(null, null);

		// ��������� ��������� ����� �� ���������
		final PrivateKey keyA = (PrivateKey) hdImageStore.getKey(CONT_NAME_RAPIDA, PASSWORD_RAPIDA);
		final PrivateKey keyB = (PrivateKey) hdImageStore.getKey(CONT_NAME_TINKOFF, PASSWORD_TINKOFF);

		System.out.println("OK");
	}

	/**
	 * ������������� �������� ����
	 * 
	 * @param algorithm
	 *            ��������
	 * @return �������� ����
	 * @throws Exception
	 *             /
	 */
	public static KeyPair genKey(String algorithm) throws Exception {

		// �������� ���������� �������� ����
		final KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);

		// ������������� �������� ����
		return keyGen.generateKeyPair();
	}

	/**
	 * ������������� �������� ���� � �����������
	 * 
	 * @param algorithm
	 *            ��������
	 * @return �������� ����
	 * @throws Exception
	 *             /
	 */
	public static KeyPair genKeyWithParams(String algorithm) throws Exception {

		final OID keyOid = new OID("1.2.643.2.2.19");
		final OID signOid = new OID("1.2.643.2.2.35.2");
		final OID digestOid = new OID("1.2.643.2.2.30.1");
		final OID cryptOid = new OID("1.2.643.2.2.31.1");

		// �������� ���������� �������� ���� ���
		final KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);

		// ����������� ���������� ���������� �������� ����
		final AlgIdSpec keyParams = new AlgIdSpec(keyOid, signOid, digestOid, cryptOid);
		keyGen.initialize(keyParams);

		// ������������� �������� ����
		return keyGen.generateKeyPair();
	}

	/**
	 * ���������� � ���������
	 * 
	 * @param pair
	 *            ��������������� �������� ����
	 * @param contName
	 *            ��� ����������
	 * @param password
	 *            ������ �� ��������
	 * @param dname
	 *            ��� �������� �����������
	 * @throws Exception
	 *             /
	 */
	public static void saveKeyWithCert(KeyPair pair, String contName, char[] password, String dname) throws Exception {

		// * �������� ������� ������������, ��������� �� ����������������
		// �����������
		final Certificate[] certs = new Certificate[1];
		certs[0] = genSelfCert(pair, dname);

		// * ������ ��������� ����� � ������� ������������ � ���������
		// ����������� ���� ��������� ��������, �� ������� ����� ������������
		// ������ �����
		final KeyStore hdImageStore = KeyStore.getInstance(Constants.KEYSTORE_TYPE);
		// �������� ����������� �������� (��������������, ��� �� ����������
		// ��������� ���������� ������������)
		hdImageStore.load(null, null);
		// ������ �� �������� ��������� ����� � �������
		hdImageStore.setKeyEntry(contName, pair.getPrivate(), password, certs);
		// ���������� ����������� ���������
		hdImageStore.store(null, null);
	}

	/**
	 * ������������� ���������������� �����������
	 * 
	 * @param pair
	 *            �������� ����
	 * @param dname
	 *            ��� �������� �����������
	 * @return ��������������� ����������
	 * @throws Exception
	 *             /
	 */
	public static Certificate genSelfCert(KeyPair pair, String dname) throws Exception {

		// �������� ���������� ���������������� �����������
		final GostCertificateRequest gr = new GostCertificateRequest();
		// ������������� ���������������� �����������, ������������� �
		// DER-���������
		final byte[] enc = gr.getEncodedSelfCert(pair, dname);
		// ������������� ���������� X509-������������
		final CertificateFactory cf = CertificateFactory.getInstance(Constants.CF_ALG);
		// ������������� X509-����������� �� ��������������� �������������
		// �����������
		return cf.generateCertificate(new ByteArrayInputStream(enc));
	}
}
