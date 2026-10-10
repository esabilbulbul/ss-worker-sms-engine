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
import java.util.ArrayList;
import jaxesa.log.LogManager;
import jaxesa.persistence.EntityManager;
import jaxesa.redis.ssoRedisLettuce;
import jaxesa.util.Util;
import misc.ftp.core;
import redis.clients.jedis.Jedis;
import ss.app.ftp.ssoFileImportRequest;
import ss.app.ftp.ssoFileImportResponse;
import ss.app.importing.ImportOps;
import ss.app.prm.ParamOps;
import ss.app.prm.ssoPath;
import ss.app.sse.SSEOps;
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
            ssoTextMessageRequest newSMSRequest = Util.Redis.JLists.pop(lettuce, sQueueName,
                    ssoTextMessageRequest.class, false);
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

                String sJSNumber = newSMSRequest.numbers;
                String sContent = newSMSRequest.content;
                
                SMSOps smsOps = SMSOps.createDefault(); // SMS
                
                org.json.JSONArray jsanNumbers = new org.json.JSONArray(sJSNumber); // Parse
                
                smsOps.sendBulkSMS(jsanNumbers, sContent); // Sending SMS
                
                long credits = smsOps.getCredits();
                
                java.util.List<String> originators = smsOps.getOriginators();
                
                java.util.List<com.bulbullers.sms.DeliveryReport> reports = smsOps.getReport("8297311291");
                
                
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

 
}