export ECLIPSE_WS="C:/Users/joset/source/repos/Modelio"
./generate-target.sh
cd AGGREGATOR
mvn clean install -Dmaven.test.skip=true
