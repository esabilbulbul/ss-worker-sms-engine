/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package sms;
//
import com.bulbullers.sms.DeliveryReport;
import com.bulbullers.sms.SmsApiException;
import com.bulbullers.sms.SmsClient;
import com.bulbullers.sms.SmsConfig;
import com.bulbullers.sms.SmsResponse;
//

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import jaxesa.log.LogManager;
import jaxesa.persistence.EntityManager;
import jaxesa.persistence.Query;
import jaxesa.redis.ssoRedisLettuce;
import jaxesa.sms.smsUtil;
import jaxesa.util.Util;
import misc.ftp.core;
import redis.clients.jedis.Jedis;
import ss.app.ftp.ssoFileImportRequest;
import ss.app.ftp.ssoFileImportResponse;
import ss.app.importing.ImportOps;
import ss.app.prm.ParamOps;
import ss.app.prm.ssoPath;
import ss.app.sse.SSEOps;
import ss.sms.ssSMSCommon;
import ss.sms.ssoSMSRequest;
import thread.worker.jeiWorkerInterface;
import thread.worker.ssoThreadSettings;

/**
 *
 * @author Administrator
 */
public class SMSCore implements jeiWorkerInterface {
    public static ArrayList<ssoPath> gaPaths = new ArrayList<ssoPath>();

    public static String IMPORTER_ACTIVE_JOBS_QUEUE = "q-sms-engine-active-jobs";// this keeps only one token at a time.
                                                                                 // Not the receival queue (Be careful)

    public static String ROOT_FILES_PATH = "";
    public static String FTP_FILE_RESULT_LISTEN_QUEUE = "q-sms-engine";// This is sent by SSE request. Once FTP
                                                                       // completed (virus passed), client comes with
                                                                       // SSE request that creates this QUEUE entry
    public static String APP_NAME = "ss-worker-sms-engine";

    boolean gResetActiveJobs = false;

    @Override
    public void init(Integer pIndex, String pName, EntityManager pem, ssoThreadSettings pSettings) {
        // CLOSED BECAUSE SYSCORE ALREADY STARTED IT IT CAN ONLY BE STARTED ONCE
        /*
         * LogManager.init(pSettings.logServer.ServerCode,
         * pSettings.logServer.ServerPath + File.separator +
         * pSettings.logServer.ServerBaseDir,
         * pSettings.logServer.LogFolder,
         * pName,//"worker_file_inv_import",//pSettings.logServer.FileName,//DONT USE
         * MANUAL NAME BECAUSE LOGMANAGER SHARED WITH SYSCORE
         * pSettings.logServer.LogServerURL,
         * pSettings.logServer.LogQueueName
         * );
         * LogManager.Start();
         */
        LogManager.SysLog(0, "**************************************");
        LogManager.SysLog(0, "Worker: 'SMS Engine Stats (" + APP_NAME + ")' starting...");

        gaPaths = ParamOps.loadFTPRootPaths(pem);

        gResetActiveJobs = true;
    }

    @Override
    public String run(EntityManager pem) {
        String sActiveToken = "";

        ssoRedisLettuce lettuce = Util.Redis.getConnection("sms-engine");

        boolean bFileReceived = false;
        ssoFileImportRequest newFile = new ssoFileImportRequest();
        ssoFileImportResponse newFileResp = new ssoFileImportResponse();

        // Util.Redis.JLists.remove(jedis, pKeys);

        try {
            // String sURL = "http://" + WEBSERVER_HOST +
            // "/bb-rest-api/rest/bulbuller/dekont/api/neweodentry/?";
            if (gResetActiveJobs == true) {
                // IMPORTANT
                // This will send fail response back to SSE listening threads
                SSEOps.resetActiveJobs(lettuce, IMPORTER_ACTIVE_JOBS_QUEUE);
                gResetActiveJobs = false;
            }

            pem.SetSessionUser("sys");

            // String sQueueName = "q-file-ftp-out";//file will be receveid in
            // 'q-file-ftp-in" then scanner will kick in will move it into "q-file-ftp-out"
            String sQueueName = FTP_FILE_RESULT_LISTEN_QUEUE;

            // boolean bImportCompletedSuccess = false;
            // newFile = Util.Redis.JLists.pop(jedis, sQueueName, ssoNewFileSaved.class,
            // false);
            // newFile = Util.Redis.JLists.pop(lettuce, sQueueName,
            // ssoFileImportRequest.class, false);
            ssoSMSRequest newSMSRequest = Util.Redis.JLists.pop(lettuce, sQueueName,ssoSMSRequest.class, false);
            if (newSMSRequest != null) {

                // ---------------------------------------------------------------------
                // IMPORTANT
                // This same queue will be used to empty at start of the worker.
                // So that SSE Servlet can get back to client and release the resources
                // ---------------------------------------------------------------------
                SSEOps.add2ActiveJobs(lettuce, IMPORTER_ACTIVE_JOBS_QUEUE, newFile.token);

                // Starting
                // ---------------------------------------------------------------------
                bFileReceived = true;
                sActiveToken = newFile.token;

                // DO JOB HERE
                // 0. Verify the Content
                
                
                // 1. READ NUMBERS + CONTENT
                // 2. PREPARE REQUEST WITH THE WANTED NUMBERS 
                //      2.1 Prepare Phone Numbers for given number of phones
                // 3. SEND IT TO SMS PROVIDER GATE
                // 4. READ MESSAGE ID FROM RESPONSE OF SMS PROVIDER 
                // 5. Write to Ledger
                // 6. UPDATE SMS CREDIT
                // 7. ADD to History

                String sJSNumber = newSMSRequest.phoneNumbers;
                String sContent = newSMSRequest.content;
                
                boolean bSourceVerified = smsUtil.verifySMSSignature(sJSNumber, newSMSRequest.hash);
                
                //if(bSourceVerified==true)
                if(true)
                {
                    SMSAhsuOps smsOps = SMSAhsuOps.createDefault(); // SMS

                    org.json.JSONArray jsanNumbers = new org.json.JSONArray(sJSNumber); // Parse

                    // PREPARE NUMBERS TO BE SENT
                    // THE NUMBERS RECEVIED CAN BE LOT MORE BUT 
                    // SERVER MAY REQUEST no of them to be sent
                    //---------------------------------------------------------
                    String sNumbers = "[";
                    long nNumberofPhones = 0;
                    
                    for(int i=0;i<newSMSRequest.noOfNumbers2Sent;i++)
                    {
                        if(i!=0)
                            sNumbers += ",";

                        String phoneN = jsanNumbers.get(i).toString();
                        sNumbers += phoneN;
                        nNumberofPhones = i;
                    }
                    sNumbers += "]";

                    org.json.JSONArray jsaFinalNumbers = new org.json.JSONArray(sNumbers); // Parse

                    //GET MSGID (bura)
                    
                    java.util.List<SmsResponse> responses = smsOps.sendBulkSMS(jsaFinalNumbers, sContent);  // Sending SMS
                    
                    String sMsId = " ";
                    
                    if(responses != null && !responses.isEmpty())
                    {
                        sMsId = SMSAhsuOps.extractMessageId(responses.get(0));
                    }
                    if (newSMSRequest.userId == null || newSMSRequest.userId.trim().isEmpty()) 
                    {
                        newSMSRequest.userId = "0";
                    }
                    
                    // WRITE LEDGER
                    ssSMSCommon.write2Ledger(BigInteger.ZERO, BigInteger.ZERO, sContent);

                    // update SMS CREDIT
                    ssSMSCommon.updateSMS4Sent(pem, new BigInteger(newSMSRequest.userId), nNumberofPhones);

                    // add 2 sms history (msgId + no(#) of phones + datetime (bura)
                    
                    String sDateTime = Util.DateTime.GetDateTime_s("yyyyMMddHHmmssSSS");
                    
                    org.json.JSONObject jsHistory = new org.json.JSONObject();
                    
                    jsHistory.put("smsId", sMsId);
                    jsHistory.put("noOfPhones", nNumberofPhones);
                    jsHistory.put("datetime", sDateTime);
                    LogManager.SysLog(0, "SMS History: " + jsHistory.toString());
                    
                    // SAVE SMS HISTORY TO DATABASE
                    // (If DB save fails, exception log is written to file inside the function)
                    
                    saveSMSHistory2DB(pem, newSMSRequest, sMsId, nNumberofPhones);
                            
                    //long credits = smsOps.getCredits();

                    //java.util.List<String> originators = smsOps.getOriginators();

                    //java.util.List<com.bulbullers.sms.DeliveryReport> reports = smsOps.getReport("8297311291");
                    
                }

                // INFORM SSE
                // ----------------------------------------------------------
                // newFileResp.token = newFile.token;
                // newFileResp.result = newFile.result;
                // newFileResp.end = true;
                Util.Redis.publish(lettuce, SSEOps.FTP_FILE_STATS_PUBLISH_CHANNEL, newFileResp);

            } // END OF NEW REQUEST

            if (sActiveToken.trim().length() != 0)
                SSEOps.deleteActiveJob(lettuce, IMPORTER_ACTIVE_JOBS_QUEUE, sActiveToken);

            Util.Redis.releaseConnection(lettuce);

            return "";

        } catch (Exception e) {
            LogManager.SysLog(-1, "Worker (bb-worker-file-importer-batch-inv) Exception:" + e.getMessage());
            if (bFileReceived == true) {
                newFileResp.token = newFile.token;
                newFile.result = "exception: " + e.getMessage();
                Util.Redis.publish(lettuce, SSEOps.FTP_FILE_STATS_PUBLISH_CHANNEL, newFileResp);
            }

            Util.Redis.releaseConnection(lettuce);
        }

        return "";
    }
    
    private void saveSMSHistory2DB(EntityManager pem, ssoSMSRequest pSMSRequest, String pMsgId, long pNumberOfPhones)
    {
        try 
        {
            String sqlHistory = "INSERT INTO ss_sms_history_TEST  "
                              + "("
                                + "UID, "
                                + "SERVER_ID, "
                                + "STAT, "
                                + "INSERTDATE, "
                                + "LASTUPDATE, "
                                + "VERSION, "
                                + "BYUSER, "
                                + "USER_ID, "
                                + "ACCOUNT_ID, "
                                + "MSG_GROUP_ID, "
                                + "QUANTITY_SMS_SENT, "
                                + "QUANTITY_SMS_SENT_VERIFIED"
                                + ") "
                              + "VALUES "
                                + "("
                                    + "?, "
                                    + "1, "
                                    + "1, "
                                    + "?, "
                                    + "?, "
                                    + "0, "
                                    + "'sys', "
                                    + "?, "
                                    + "?, "
                                    + "?, "
                                    + "?, "
                                    + "0"
                                + ")";
            
            long now = Long.parseLong(Util.DateTime.GetDateTime_s("yyyyMMddHHmmss"));
            
            BigInteger historyUid = new BigInteger(Util.DateTime.GetDateTime_s("yyyyMMddHHmmssSSS"));
            
            BigInteger dbUserId   = (pSMSRequest.userId != null && !pSMSRequest.userId.trim().isEmpty()) 
                                    ? new BigInteger(pSMSRequest.userId.trim()) 
                                    : BigInteger.ZERO;
            
            BigInteger dbAccId    = (pSMSRequest.accId != null && !pSMSRequest.accId.trim().isEmpty()) 
                                    ? new BigInteger(pSMSRequest.accId.trim()) 
                                    : BigInteger.ZERO;
            
            BigInteger dbMsgId    = BigInteger.ZERO;
            if (pMsgId != null && !pMsgId.trim().isEmpty()) 
            {
                String numDigits = pMsgId.trim().replaceAll("\\D+", "");
                
                if (!numDigits.isEmpty())
                {
                    dbMsgId = new BigInteger(numDigits);
                }
            }
            
            Query qry = pem.CreateNativeQuery(sqlHistory);
            
            qry.SetParameter(1, historyUid,       "UID");
            qry.SetParameter(2, now,              "INSERTDATE");
            qry.SetParameter(3, now,              "LASTUPDATE");
            qry.SetParameter(4, dbUserId,         "USER_ID");
            qry.SetParameter(5, dbAccId,          "ACCOUNT_ID");
            qry.SetParameter(6, dbMsgId,          "MSG_GROUP_ID");
            qry.SetParameter(7, pNumberOfPhones,  "QUANTITY_SMS_SENT");
            qry.executeUpdate();
            
            LogManager.SysLog(0, "SMS History Saved ---> UID: " + historyUid);
            
        } 
        catch (Exception e) 
        {
            // Write exception log (file) because SMS sent but not saved to database.
            // ssoSMSCreditExceptionLog
            String sFileName  = ssSMSCommon.SMS_LEDGER_FILE_PATH + File.separator 
                              + "ss_sms_credit_exception_" 
                              + Util.DateTime.GetDateTime_s("yyMMddHHmm") 
                              + ".log";
            
            ssoSMSCreditExceptionLog newCreditExceptionLog = new ssoSMSCreditExceptionLog();
            newCreditExceptionLog.userId      = (pSMSRequest.userId != null) ? pSMSRequest.userId.trim() : "";
            newCreditExceptionLog.accId       = (pSMSRequest.accId  != null) ? pSMSRequest.accId.trim()  : "";
            newCreditExceptionLog.quantitySMS = String.valueOf(pNumberOfPhones);
            newCreditExceptionLog.errDesc     = "dateTime: " + Util.DateTime.GetDateTime_s("yyyyMMddHHmmssSSS")
                                              + " | msgId: " + ((pMsgId != null) ? pMsgId.trim() : "")
                                              + " | error: " + e.getMessage();
            
            String sFileData = Util.JSON.toString(newCreditExceptionLog);
            Util.Files.Write2File(sFileName, sFileData);
            
            LogManager.SysLog(-1, "DB SAVED FAILED " + e.getMessage());
        }
    }
 
}