import java.io.*;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.util.Iterator;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;

import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
/*This file is 𝕌𝕋𝔽-𝟠 encoded*/

@SuppressWarnings("serial")
@MultipartConfig(fileSizeThreshold=1024*1024*10,  // 10 MB 
                 maxFileSize=-1/*1024*1024*50*/,       // 50 MB
                 maxRequestSize=-1/*1024*1024*100*/)    // 100 MB

public class opennlpPOSTagger extends HttpServlet 
    {    
    // Static logger object.  
    private static final Logger logger = LoggerFactory.getLogger(opennlpPOSTagger.class);
    private static final String TMP_DIR_PATH = "/tmp";
    
    private String MODEL_DIR = "/WEB-INF/model/";
    Map<String, POSTaggerME> taggers;
    
    public String readProperty(String property)
        {
        logger.debug("readProperty [{}]",property);
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
        logger.debug("init");
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
        logger.debug("tagSentence {}: {}",sent[0],language);
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
        logger.debug("POStag {},{}",arg,language);
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
        logger.debug("doGet");
        PrintWriter out = response.getWriter();
        out.println("GET not supported");
        }

     public static String getParmFromMultipartFormData(HttpServletRequest request,Collection<Part> items,String parm)
        {
        logger.debug("getParmFromMultipartFormData:["+parm+"]");
        String ret = "";
        try 
            {
            logger.debug("items:"+items);
            Iterator<Part> itr = items.iterator();
            logger.debug("itr:"+itr);
            while(itr.hasNext()) 
                {
                logger.debug("in loop");
                Part item = itr.next();
                logger.debug("Field Name = "+item.getName()+", String = "+IOUtils.toString(item.getInputStream(),StandardCharsets.UTF_8));
                if(item.getName().equals(parm))
                    {
                    ret = IOUtils.toString(item.getInputStream(),StandardCharsets.UTF_8);
                    logger.debug("Found " + parm + " = " + ret);
                    break; // currently not interested in other fields than parm
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

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
//        throws ServletException, IOException 
        {
        logger.debug("doPost");
        Collection<Part> items = null;
        try 
            {
            logger.debug("getParts");
            items = request.getParts(); // throws ServletException if this request is not of type multipart/form-data
            logger.debug("gotParts");

            logger.debug("doPost got items");

            String language = getParmFromMultipartFormData(request,items,"lang");
            PrintWriter out = response.getWriter();
            response.setContentType("text/plain;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(200);
            logger.debug("doPost, RemoteAddr == {}",request.getRemoteAddr());

            String arg  = getParmsAndFiles(items,response,out);

            logger.debug("Call POStag({},{})",arg,language);

            POStag(arg,out,language);
            /*File toDelete = new File(arg);
            toDelete.delete();*/
            }
        catch(IOException ex) 
            {
            logger.error("Error encountered while parsing the request: "+ex.getMessage());
            return;
            }
        catch(ServletException ex) 
            {
            logger.error("Error encountered while parsing the request: "+ex.getMessage());
            return;
            }
        }

    public java.lang.String getParmsAndFiles(Collection<Part> items,HttpServletResponse response,PrintWriter out) throws ServletException
        {       
        logger.debug("getParmsAndFiles");

        java.lang.String arg = "";

        try {
            // Parse the request
            Iterator<Part> itr = items.iterator();
            while(itr.hasNext()) 
                {
                logger.debug("in loop");
                Part item = itr.next();
                // Handle Form Fields.
                if(item.getSubmittedFileName() != null)
                    {
                    //Handle Uploaded files.
                    String LocalFileName = item.getSubmittedFileName();
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
                    item.write(filename);
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
