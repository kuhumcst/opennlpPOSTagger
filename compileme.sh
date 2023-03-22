ant clean-all
ant -Divy=true download-ivy 
env CATALINA_HOME=/opt/tomcat/apache-tomcat-9.0.30/ ant -Divy=true -Dprod war
