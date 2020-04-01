import java.io.*;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import is2.data.Cluster;
import is2.data.DataF;
import is2.data.DataFES;
import is2.data.F2SF;
import is2.data.FV;
import is2.data.Instances;
import is2.data.Long2Int;
import is2.data.Long2IntInterface;
import is2.data.Parse;
import is2.data.PipeGen;
import is2.data.SentenceData09;
import is2.io.CONLLReader09;
import is2.io.CONLLWriter09;
import is2.tools.Retrainable;
import is2.tools.Tool;
import is2.util.DB;
import is2.util.OptionsSuper;
import is2.util.ParserEvaluator;

import is2.parser.*;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.httpclient.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
/*বাংলা this file is UTF-8 encoded*/


public class opennlpPOSTagger extends HttpServlet 
    {    
    // Static logger object.  
    private static final Logger logger = LoggerFactory.getLogger(opennlpPOSTagger.class);
    private static final String TMP_DIR_PATH = "/tmp";
    
    private String MODEL_DIR = "/WEB-INF/model/";
    Map<String, POSTaggerME> taggers;
    
    public String readProperty(String property)
        {
        String ret = null;
        try
            {
            FileInputStream fis = new java.io.FileInputStream(getServletContext().getRealPath("/WEB-INF/classes/properties.xml"));
            Properties prop = new Properties();
            try 
                {
                prop.loadFromXML(fis);
                ret = prop.getProperty(property,"");
                }
            catch (IOException io)
                {
                logger.warn("could not read properties. Message is " + io.getMessage()); 
                }
            finally
                {
                fis.close();
                }
            }
        catch (IOException io)
            {
            logger.warn("Could not read properties file. Message is " + io.getMessage()); 
            }    
        return ret;
        }

    public void init(ServletConfig config) throws ServletException 
        {
        super.init(config);
        taggers = new ConcurrentHashMap<String, POSTaggerME>();

        String[] languages = readProperty("languages").split(",");

        for(int i = 0 ;i < languages.length ; i++)
            {
            String lang = languages[i];
            //String relativeWebPath = POS_TAG_MODEL;
            String relativeWebPath = MODEL_DIR + readProperty(lang);
            String absoluteDiskPath = getServletContext().getRealPath(relativeWebPath);
            logger.debug("init, absoluteDiskPath == {}",absoluteDiskPath);
        
            InputStream modelIn = null;

            try {
                logger.debug("try");
            
                modelIn = new FileInputStream(absoluteDiskPath);
                POSModel model = new POSModel(modelIn);
                POSTaggerME tagger = new POSTaggerME(model);
                taggers.put(lang, tagger);
                }
            catch (IOException e) 
                {
                // Model loading failed, handle the error
                e.printStackTrace();
                }
            finally 
                {
                if (modelIn != null)
                    {
                    try {
                        modelIn.close();
                        }
                    catch (IOException e) 
                        {
                        }
                    }
                }    
            }    
        }


    public void tagSentence(String sent[],PrintWriter out,String language)
        {
        if(sent.length > 0 && !(sent[0].equals("")))
            {
            POSTaggerME tagger;
            tagger = taggers.get(language);
            String tags[] = tagger.tag(sent);        
            for (String x: tags)
                out.print(x+" ");
            }
        }

    public void POStag(String arg,PrintWriter out,String language)
    throws IOException
        {
        try {
            BufferedReader dis = new BufferedReader(new InputStreamReader(new FileInputStream(arg),"UTF8"));
            String str;
            while ((str = dis.readLine()) != null) 
                {
                tagSentence(str.split(" "),out,language);
                out.print("\n");
                }
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(arg));
            }
        catch(UnsupportedEncodingException ue)
            {
            logger.error("Not supported : "+ue.getMessage());
            }
        catch(IOException e)
            {
            logger.error("FileNotFoundException: "+e.getMessage());
            }
        }


    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
        {
        PrintWriter out = response.getWriter();
        out.println("GET not supported");
        }

     public static String getParmFromMultipartFormData(HttpServletRequest request,List<FileItem> items,String parm)
        {
        logger.debug("parm:["+parm+"]");
        String ret = "";
        try 
            {
            logger.debug("items:"+items);
            Iterator<FileItem> itr = items.iterator();
            logger.debug("itr:"+itr);
            while(itr.hasNext()) 
                {
                logger.debug("in loop");
                FileItem item = (FileItem) itr.next();
                if(item.isFormField()) 
                    {
                    logger.debug("Field Name = "+item.getFieldName()+", String = "+item.getString());
                    if(item.getFieldName().equals(parm))
                        {
                        ret = item.getString();
                        logger.debug("Found " + parm + " = " + ret);
                        break; // currently not interested in other fields than parm
                        }
                    }
                }
            }
        catch(Exception ex) 
            {
            logger.error("uploadHandler.parseRequest Exception");
            }
        logger.debug("value["+parm+"]="+ret);
        return ret;
        }             
                   

 
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
        {
        List items = getParmList(request);
        String language = getParmFromMultipartFormData(request,items,"lang");
        PrintWriter out = response.getWriter();
        response.setContentType("text/plain;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);
        logger.debug("doPost, RemoteAddr == {}",request.getRemoteAddr());

        java.lang.String arg  = getParmsAndFiles(items,response,out);
        POStag(arg,out,language);
        /*File toDelete = new File(arg);
        toDelete.delete();*/
        }

    public static List getParmList(HttpServletRequest request) throws ServletException
        {
        List<FileItem> items = null;
        
        Enumeration<String> parmNames = (Enumeration<String>)request.getParameterNames();
        for (Enumeration<String> e = parmNames ; e.hasMoreElements() ;) 
            {
            String parmName = e.nextElement();
            logger.debug("parmName: " + parmName);            
            String vals[] = request.getParameterValues(parmName);
            for(int j = 0;j < vals.length;++j)
                {
                logger.debug("value: " + vals[j]);            
                }
            }
        
        boolean is_multipart_formData = ServletFileUpload.isMultipartContent(request);

        if(is_multipart_formData)
            {
            DiskFileItemFactory  fileItemFactory = new DiskFileItemFactory ();
            // Set the size threshold, above which content will be stored on disk.
            fileItemFactory.setSizeThreshold(1*1024*1024); //1 MB
            // Set the temporary directory to store the uploaded files of size above threshold.
            File tmpDir = new File(TMP_DIR_PATH);
            if(!tmpDir.isDirectory()) 
                {
                throw new ServletException("Trying to set \"" + TMP_DIR_PATH + "\" as temporary directory, but this is not a valid directory.");
                }
            fileItemFactory.setRepository(tmpDir);

            
            ServletFileUpload uploadHandler = new ServletFileUpload(fileItemFactory);
            try {
                items = uploadHandler.parseRequest(request);
                }
            catch(FileUploadException ex) 
                {
                logger.error("Error encountered while parsing the request: "+ex.getMessage());
                }
            }
        return items;
        }

    public java.lang.String getParmsAndFiles(List items,HttpServletResponse response,PrintWriter out) throws ServletException
        {        
        java.lang.String arg = "";

        try {
            // Parse the request
            Iterator itr = items.iterator();
            while(itr.hasNext()) 
                {
                logger.debug("in loop");
                FileItem item = (FileItem) itr.next();
                // Handle Form Fields.
                if(item.isFormField()) 
                    {
                    logger.debug("form field:"+item.getName());                    
                    }
                else if(item.getName() != "")
                    {
                    //Handle Uploaded files.
                    String LocalFileName = item.getName();
                    logger.debug("LocalFileName:"+LocalFileName);
                    // Write file to the ultimate location.

                    File tmpDir = new File(TMP_DIR_PATH);
                    if(!tmpDir.isDirectory()) 
                        {
                        throw new ServletException("Trying to set \"" + TMP_DIR_PATH + "\" as temporary directory, but this is not a valid directory.");
                        }

                    File file = File.createTempFile(LocalFileName,null,tmpDir);
                    String filename = file.getAbsolutePath();
                    logger.debug("LocalFileName:"+filename);
                    item.write(file);
                    arg = filename;
                    }
                }
            }
        catch(Exception ex) 
            {
            }
        return arg;
        }
    }
