/**
 * $RCSfile: Constants.java,v $
 * version $Revision: 1.5 $
 * created 26.05.2009 15:07:10 by kunina
 * last modified $Date: 2009/09/25 13:12:14 $ by $Author: kunina $
 * (C) ��� ������-��� 2004-2009.
 *
 * ����������� ���, ������������ � ���� �����, ������������
 * ��� ����� ��������. ����� ���� ���������� ��� ������������� 
 * ��� ������� ���������� ������� � ��������� ��������� � ����.
 *
 * ������ ��� �� ����� ���� ��������������� �����������
 * ��� ������ ����������. �������� ������-��� �� ����� �������
 * ��������������� �� ���������������� ����� ����.
 */
package com.luxoft.pkcs7.cms.tests;

/**
 * ��������� ��� ��������.
 *
 * @author Copyright 2004-2009 Crypto-Pro. All rights reserved.
 * @.Version
 */
public class Constants {
/** ��������� **/
/**
 * �������� ����� �������: "GOST3410" ��� JCP.GOST_DEGREE_NAME
 */
public static final String SIGN_KEY_PAIR_ALG = "GOST3410";
/**
 * �������� ����� ������: "GOST3410DH" ��� JCP.GOST_DH_NAME
 */
public static final String EXCH_KEY_PAIR_ALG = "GOST3410DH";
/**
 * �������� ����������� "X509" ��� JCP.CERTIFICATE_FACTORY_NAME
 */
public static final String CF_ALG = "X509";
/**
 * �������� ���������� ���� 28147-89: "GOST28147" ��� CryptoProvider.GOST_CIPHER_NAME
 */
public static final String CHIPHER_ALG = "GOST28147";
/**
 * �������� ����������� ���� � 34.11-94: "GOST3411" ��� JCP.GOST_DIGEST_NAME
 */
public static final String DIGEST_ALG = "GOST3411";
/**
 * �������� ������� ���� � 34.10-2001: "GOST3411withGOST3410EL" ���
 * JCP.GOST_EL_SIGN_NAME
 */
public static final String SIGN_EL_ALG = "GOST3411withGOST3410EL";
/**
 * �������� ������� ���� � 34.10-2001 (������������ ��� �������������� �
 * ����������������� CryptoPro CSP): "CryptoProSignature" ���
 * JCP.CRYPTOPRO_SIGN_NAME
 */
public static final String SIGN_CP_ALG = "CryptoProSignature";

/**
 * ������ ��������� �����: "CPRandom" ��� JCP.CP_RANDOM
 */
public static final String RANDOM_ALG = "CPRandom";

/** ��������� ��� ������ � ������� � ������������� **/
/**
 * ��� ���������:
 * <p/>
 * "HDImageStore" - ������� ����
 * <p/>
 * "FloppyStore" - �������, ������
 * <p/>
 * "OCFStore" ��� "J6CFStore" - ��������
 */
public static final String KEYSTORE_TYPE = "HDImageStore";

/** ��������������� ������� � ��������� **/
/**
 * hex-string
 *
 * @param array ������ ������
 * @return hex-string
 */
public static String toHexString(byte[] array) {
    final char[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A',
            'B', 'C', 'D', 'E', 'F'};
    StringBuffer ss = new StringBuffer(array.length * 3);
    for (int i = 0; i < array.length; i++) {
        ss.append(' ');
        ss.append(hex[(array[i] >>> 4) & 0xf]);
        ss.append(hex[array[i] & 0xf]);
    }
    return ss.toString();
}
}
