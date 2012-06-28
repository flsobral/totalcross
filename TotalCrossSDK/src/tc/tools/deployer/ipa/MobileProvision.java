package tc.tools.deployer.ipa;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.cert.CertificateFactory;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.jce.provider.X509CertificateObject;
import com.dd.plist.*;

public class MobileProvision
{
   public final String applicationIdentifierPrefix;
   public final NSDate creationDate;
   public final X509CertificateObject[] developerCertificates;
   public final NSDictionary entitlements;
   public final NSDate expirationDate;
   public final String name;
   public final boolean provisionsAllDevices;
   public final String[] provisionedDevices;
   public final String[] teamIdentifier;
   public final int timeToLive;
   public final String uuid;
   public final int version;

   public final String bundleIdentifier;

   private final String content;

   public MobileProvision(String content) throws Exception
   {
      this.content = content;

      NSDictionary rootDictionary = (NSDictionary) PropertyListParser.parse(content.getBytes());

      // ApplicationIdentifierPrefix
      NSArray array = (NSArray) rootDictionary.objectForKey("ApplicationIdentifierPrefix");
      applicationIdentifierPrefix = array.count() > 0 ? array.objectAtIndex(0).toString() : null;

      // CreationDate
      creationDate = (NSDate) rootDictionary.objectForKey("CreationDate");

      // DeveloperCertificates
      CertificateFactory cf = CertificateFactory.getInstance("X509", "BC");
      array = (NSArray) rootDictionary.objectForKey("DeveloperCertificates");
      developerCertificates = array != null ? new X509CertificateObject[array.count()] : null;
      if (developerCertificates != null && developerCertificates.length > 0)
      {
         NSObject[] certificates = array.getArray();
         for (int i = 0; i < certificates.length; i++)
         {
            ByteArrayInputStream certificateData = new ByteArrayInputStream(((NSData) certificates[i]).bytes());
            developerCertificates[i] = (X509CertificateObject) cf.generateCertificate(certificateData);
         }
      }

      // Entitlements
      entitlements = (NSDictionary) rootDictionary.objectForKey("Entitlements");

      // ExpirationDate
      expirationDate = (NSDate) rootDictionary.objectForKey("ExpirationDate");

      // Name
      NSObject item = rootDictionary.objectForKey("Name");
      this.name = item != null ? item.toString() : "(unknown)";

      // ProvisionsAllDevices
      NSNumber number = (NSNumber) rootDictionary.objectForKey("ProvisionsAllDevices");
      provisionsAllDevices = number != null ? number.boolValue() : false;

      // ProvisionedDevices
      array = (NSArray) rootDictionary.objectForKey("ProvisionedDevices");
      provisionedDevices = array != null ? new String[array.count()] : null;
      if (provisionedDevices != null && provisionedDevices.length > 0)
      {
         NSObject[] devices = array.getArray();
         for (int i = 0; i < devices.length; i++)
            provisionedDevices[i] = devices[i].toString();
      }

      // TeamIdentifier
      array = (NSArray) rootDictionary.objectForKey("TeamIdentifier");
      teamIdentifier = array != null ? new String[array.count()] : null;
      if (teamIdentifier != null && teamIdentifier.length > 0)
      {
         NSObject[] team = array.getArray();
         for (int i = 0; i < team.length; i++)
            teamIdentifier[i] = team[i].toString();
      }

      // TimeToLive
      number = (NSNumber) rootDictionary.objectForKey("TimeToLive");
      timeToLive = number != null ? number.intValue() : -1;

      // UUID
      item = rootDictionary.objectForKey("UUID");
      uuid = item != null ? item.toString() : null;

      // Version
      number = (NSNumber) rootDictionary.objectForKey("Version");
      version = number != null ? number.intValue() : -1;

      // Entitlements > application-identifier
      String s = entitlements.objectForKey("application-identifier").toString();
      bundleIdentifier = s.substring(s.indexOf('.') + 1);
   }

   public String toString()
   {
      return content;
   }

   public String GetEntitlementsString()
   {
      NSDictionary XCentPList = new NSDictionary();
      String[] keys = entitlements.allKeys();
      for (int i = 0; i < keys.length; i++)
      {
         String key = keys[i];
         NSObject item = entitlements.objectForKey(key);
         XCentPList.put(key, item);
      }
      return XCentPList.toXMLPropertyList();
   }

   public static MobileProvision readFromFile(File input) throws Exception
   {
      byte[] inputData = FileUtils.readFileToByteArray(input);
      String inputString = new String(inputData, "UTF-8");

      int startIdx = inputString.indexOf("<?xml");
      if (startIdx == -1)
         return null;

      int length = (inputData[startIdx - 2] << 8) | inputData[startIdx - 1];
      int endIdx = inputString.lastIndexOf('>', length + startIdx) + 1;
      inputString = inputString.substring(startIdx, endIdx);

      return new MobileProvision(inputString);
   }
}