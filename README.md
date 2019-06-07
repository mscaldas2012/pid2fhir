## Preface
This project is part of the FHIR in Population Health Ecosystem presentation for HL7 FHIR Dev Days in Redmond, WA
June 12, 2019.

#Intro
This project is a simple Kafka Streamer that listens to an incoming topic receiving HL7 ADT messages, extracts patient 
information and build a FHIR R4 Patient Resource posted at another topic for further processing.

#Requirements
At the writing of this documentation, here are the following requirements and versions:
* Java 8 (tested with Java 1.9.0_102-b14)
* Docker (tested with Docker 18.09.2)
* Docker-compose (tested with versin 1.23.2)
* Maven (tested with 3.3.9)

#Configuration
There are two files you might want to check for configuration.
## Docker-compose.yml
This file holds the configuration to run kafak and Zookeeper as docker containers.
Make sure you have the appropriate volumes created on your local system before running the containers. By default, you 
need to create the following folders:
- kafka-data
- kafka-logs
- zookeeper-data
- zookeeper-logs

Also, Make sure you provide an appropriate IP Address for property KAFKA_ADVERTISED_LISTENERS on line 24. You must 
replace ${MY_IP} with your local ip address.

## application.yml
Before building the project, make sure you are OK with the configuration parameters on this file.
If you have Kafka running somewhere else, you can change teh kafka.bootstrap.servers property to point to your own kafka instance.  

Also, if you would like to change the topic names for either incoming or outgoing topics, feel free to change them on that file.
****

#Build
This project uses maven. 

To build the project, run
 
    mvn clean package
To run the project:
1 - Make sure you have Kafka and zookeeper running. You may use the docker-compose.yml file on this project:
    run 
    
    docker-compose up -d 
    
**NOTE**: Before running docker-compose, make sure you have the appropriate configuration as described on the section above. 

Once kafka is up and running, you can execute the app.
you can run

    mvn exec:exec
or 

    java -jar target\pid2phir-0.1.jar
    
#Testing
Some files are provided for you to be able to test it with minimal resources available.
Feel free to use a simple ADT file under test\resources\PatientAdmission.hl7 for content. 

Some basic commands are available under test\kafka-commands.txt using the CLI tool within the container.

Follow the instructions on that file to create a topic, post a message, and consume messages on a topic.
**NOTE**: To use the CLI here, simply post the PID segment only, since this method does not allow you to provide the new line 
feeds needed to separate each segment.    
    
 