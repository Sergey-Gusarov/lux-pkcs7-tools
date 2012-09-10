package com.luxoft.pkcs7.cms.tests;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.Principal;
import java.security.Security;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorResult;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ValidateCRL {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		System.setProperty("http.proxyHost", "localhost");
		System.setProperty("http.proxyPort", "9080");
		
		Security.setProperty("ocsp.enable", "true");
        
        System.setProperty("com.sun.security.enableCRLDP", "true");
        System.setProperty("com.ibm.security.enableCRLDP", "true");
        boolean enable_revokation = true;
	  
	  
        // ���������� ��� ����������� ��������� �����
        final String aliasEndCert = "piv";
        
        
        
       //������������� ��������� ���������� ������������ � ��������� ��������
        final KeyStore keyStore = KeyStore.getInstance("HDImageStore");

        // �������� ����������� ��������� (��������������, ��� ���������,
        // ��������������������� ������ STORE_TYPE ����������) � �����������
        // ��������� ��������
        keyStore.load(new FileInputStream("C:\\var\\CPROcsp\\certstore"), null);
        
        // ������ ��������� ����������� (����������� ��������� �����) � ��������
        // (��������������, ��� ���������� ����� ���������� ���������� �� ��������)
        final X509Certificate certEnd = (X509Certificate) keyStore.getCertificate(aliasEndCert);
        certEnd.checkValidity();
        
        if (certEnd instanceof X509Certificate) {
            X509Certificate x509cert = (X509Certificate)certEnd;

            // Get subject
            Principal principal = x509cert.getSubjectDN();
            String subjectDn = principal.getName();
            System.out.println("subjectDn: " + subjectDn);

            // Get issuer
            principal = x509cert.getIssuerDN();
            String issuerDn = principal.getName();
            System.out.println("getIssuerDN: " + issuerDn);
        }
        
        //���������� ������� �� ����������� ������������, ������� � ��������� �����������
        //(� ������ aliasRootCert) � ���������� ������������ ��������� ����� (c ������ aliasEndCert)

        // ����������� ������ ������������, �� �������
        // �������������� ���������� �������
        final List<Certificate> certs = new ArrayList<Certificate>(3);
        //certs.add(certEnd);

        Set<TrustAnchor> trustAnchors = new HashSet<TrustAnchor>();
        Enumeration<String> aliases = keyStore.aliases();
        while(aliases.hasMoreElements()) {
        	String alias = aliases.nextElement();
        	if (!alias.startsWith("cacer")) {
        		continue;
        	}
        	Certificate c = keyStore.getCertificate(alias);
        	try {
        		((X509Certificate)c).checkValidity();
        	} catch (java.security.cert.CertificateExpiredException cee) {
        		System.out.println(alias + " expired " + cee.getMessage());
        		continue;
        	}
	        // ����������� ��������� ����������� (� �������� ���������� ����������
	        // �������)
        	//if (alias.equals("cacer5")) {
        	TrustAnchor anchor = new TrustAnchor((X509Certificate) c, null);
        	trustAnchors.add(anchor);
        	//certs.add(c);
        	System.out.println("Cert trusted: " + alias);
        	//}
        }

        // ����������� ���������� ������������ ���������
        // ������������, � ������� ������������ ��� ������������
        // � ���������� ������� �����������
        final CollectionCertStoreParameters par =
                new CollectionCertStoreParameters(certs);

        // �������� ������������ ��������� ������������ �� ������
        // ����������, ������������ ������� ������������
        final CertStore store = CertStore.getInstance("Collection", par);

        // ������������� ������� ���������� ������� ������������
        final CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
        //��� ��� ������������� � ��������� ��
        //CertPathBuilder cpb = CertPathBuilder.getInstance("CPPKIX");

        // ������������� ���������� ���������� ������� ������������
        final PKIXBuilderParameters params = new PKIXBuilderParameters(trustAnchors, new X509CertSelector());

        // ���������� � ���������� ������������, �� �������
        // ����� ��������� �������
        params.addCertStore(store);

        // ������������� ������� ������� �����������, �������
        // ������������� ���������� �������
        final X509CertSelector selector = new X509CertSelector();

        // ����������� �����������, �������
        // ������������� ���������� �������
        selector.setCertificate((X509Certificate) certEnd);

        params.setTargetCertConstraints(selector);
        
        params.setSigProvider("JCP");
        
        params.setRevocationEnabled(enable_revokation);
        
        

        // ���������� ������� ������������
        final PKIXCertPathBuilderResult res = (PKIXCertPathBuilderResult) cpb.build(params);
        System.out.println(res);
        CertPath cp = res.getCertPath();
        System.out.println("CertPath size : " + cp.getCertificates().size());
        /* �������� ����������� ������� ������������ */

        // ������������� ������� �������� ������� ������������
        final CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        //��� ��� ������������� � ��������� ��
        //CertPathValidator validator = CertPathValidator.getInstance("CPPKIX");
        

        // �������� ������� ������������
        final CertPathValidatorResult val_res = validator.validate(res.getCertPath(), params);

        // ����� ���������� �������� � ������� ����
        System.out.println("\n\n\n");
        System.out.println(val_res.toString());
	    
	}
	
}
