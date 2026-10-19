/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package sms;

import com.bulbullers.sms.DeliveryReport;
import com.bulbullers.sms.SmsApiException;
import com.bulbullers.sms.SmsClient;
import com.bulbullers.sms.SmsConfig;
import com.bulbullers.sms.SmsResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jaxesa.log.LogManager;

/**
 *
 * @author Administrator
 */
public class SMSAhsuOps {
    
    private static final Pattern MESSAGE_ID = Pattern.compile("(?i)ID\\s*:\\s*(\\d+)");
    
    private final SmsClient client;
    
    public SMSAhsuOps(SmsConfig config) {
        this.client = new SmsClient(config);
    }
    
    public SMSAhsuOps(String username, String password, String sender, int action) {
            this(SmsConfig.builder()
                .username(username)
                .password(password)
                .sender(sender)
                .action(action)
                .build());
    }
    
    public static SMSAhsuOps createDefault() {
        
        return new SMSAhsuOps("5334770727", "5334770727", "BULBULLER", 12);
    }
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    //                  SMS GONDERIM
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    
    public SmsResponse sendSMS(String phone, String content) {
        
        try {
            SmsResponse response = client.send(phone, content);
            LogManager.SysLog(0,  "SMS Confirm ---> Number : " + phone +
                                  " | Status : " + response.statusCode() +
                                  " | Yanit : " + response.body());
            return response;
        }catch (InterruptedException ie) {
           
            Thread.currentThread().interrupt();
            LogManager.SysLog(-1, "SMS Gonderim Kesildi -> Tel: " + phone);
            
        }catch (Exception ex) {
         
            LogManager.SysLog(-1,   "SMS Gonderim Hatası -> Tel: " + phone +
                                    " | Hata: " + ex.getMessage());
        }
        
        return null;
    }
    // JSON dizisindeki tum numaralara ayni icerigi gonderir. 
    
    public List<SmsResponse> sendBulkSMS(org.json.JSONArray jsanNumbers, String content) {
        
        List<SmsResponse> responses = new ArrayList<>();
        
        for (int i = 0; i < jsanNumbers.length(); i++) {
            
            String rawPhone = jsanNumbers.get(i).toString();
            SmsResponse response = sendSMS(rawPhone, content);
            
            if (response != null) 
            {
                responses.add(response);
            }
        }
        return responses;
    }
    /** Liste halindeki numaralara ayni icerigi gonderir. */
    
    public List<SmsResponse> sendBulkSMS(List<String> phones, String content) {
        
        List<SmsResponse> responses = new ArrayList<>();
        
        for (String phone : phones) {
            
            SmsResponse response = sendSMS(phone, content);
            
            if (response != null) {
                
                responses.add(response);
            }
        }
        return responses;
    }
    
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    //                                       RAPOR (ID BAZLI)
    //  Mesaj ID'si ("198784" veya "ID: 198784") icin teslim raporu. Hata olursa bos liste. */
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    
    public List<DeliveryReport> getReport(String messageId) {
        try {
            List<DeliveryReport> reports = client.report(messageId);
            
            for (DeliveryReport r : reports) {
                LogManager.SysLog(0, "SMS Rapor ---> ID : " + messageId +
                                     " | Number : " + r.phone() +
                                     " | Durum : " + r.status().description());
            }
            return reports;
            
        } catch (SmsApiException apiEx) {
            
            LogManager.SysLog(-1, "SMS Rapor API Hatasi -> ID: " + messageId +
                                  " | Kod: " + apiEx.errorCode() + " | " + apiEx.description());
            
        } catch (InterruptedException ie) {
            
            Thread.currentThread().interrupt();
            LogManager.SysLog(-1, "SMS Rapor Kesildi -> ID: " + messageId);
            
        } catch (Exception ex) {
            
            LogManager.SysLog(-1, "SMS Rapor Hatası -> ID: " + messageId +
                                  " | Hata: " + ex.getMessage());
            
        }
        return Collections.emptyList();
    }
    
    public static String extractMessageId(SmsResponse response) {
        
        if (response == null || response.body() == null) {
            
            return null;
        }
        
        Matcher m = MESSAGE_ID.matcher(response.body());
        return m.find() ? m.group(1) : null;
    }
    
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    //                          ORIGINATOR (BASLIK) SORGULAMA
    //          Hesaba tanimli gonderici basliklari. Hata olursa bos liste. 
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    
    public List<String> getOriginators() {
        try {
            List<String> originators = client.originators();
            LogManager.SysLog(0, "SMS Originator ---> " + String.join(", ", originators));
            return originators;
            
        } catch (SmsApiException apiEx) {
            
            LogManager.SysLog(-1, "SMS Originator API Hatası -> Kod: " + apiEx.errorCode() +
                                  " | " + apiEx.description());
            
        } catch (InterruptedException ie) {
            
            Thread.currentThread().interrupt();
            LogManager.SysLog(-1, "SMS Originator Sorgusu Kesildi");
            
        } catch (Exception ex) {
            
            LogManager.SysLog(-1, "SMS Originator Hatası -> " + ex.getMessage());
        }
        return Collections.emptyList();
    }
    
    /** Verilen baslik hesapta tanimli mi? */
    
    public boolean hasOriginator(String originator) {
        
        if (originator == null) {
            return false;
        }
        for (String o : getOriginators()) {
            
            if (o.equalsIgnoreCase(originator.trim())) {
                return true;
            }
        }
        return false;
    }
    
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    //                          KONTOR SORGULAMA
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    
    public long getCredits() {
        try {
            long credits = client.credits();
            LogManager.SysLog(0, "SMS Kontor ---> " + credits);
            return credits;
        } catch (SmsApiException apiEx) {
            LogManager.SysLog(-1, "SMS Kontor API Hatası -> Kod: " + apiEx.errorCode() +
                    " | " + apiEx.description());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LogManager.SysLog(-1, "SMS Kontor Sorgusu Kesildi");
        } catch (Exception ex) {
            LogManager.SysLog(-1, "SMS Kontor Hatası -> " + ex.getMessage());
        }
        return -1;
    }
    /** Gonderim icin yeterli kontor var mi? */
    public boolean hasEnoughCredits(long required) {
        long credits = getCredits();
        return credits >= 0 && credits >= required;
    }
}

