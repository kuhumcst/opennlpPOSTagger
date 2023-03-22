if [ -d /opt/tomcat10/latest/lib ]; then
    CATALINA_BASE=/opt/tomcat10/latest
    CATALINA_HOME=/opt/tomcat10/latest
fi
export CATALINA_BASE
export CATALINA_HOME
echo CATALINA_BASE $CATALINA_BASE
ant clean-all
ant -Divy=true download-ivy 
ant -Divy=true -Dprod war
sudo cp war/opennlpPOSTagger.war $CATALINA_BASE/webapps

