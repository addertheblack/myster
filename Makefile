DESTINATION=classes
JAVAC=jikes +E
#JAVAC=javac

SRCFILES = $(shell find -name \*.java)

LIBRARIES = lib \
	lib/collections.jar \
	lib/hellikerID3v2.jar

EXTRAFILES = ./com/myster/server/stream/firewall.gif \
	./com/myster/server/stream/queued.gif \
	./com/general/tab/tab_background.jpg \
	./com/general/tab/left.gif \
	./com/general/tab/graphs.gif \
	./com/general/tab/middle.gif \
	./com/general/tab/outbound.gif \
	./com/general/tab/right.gif \
	./com/general/tab/serverstats.gif \
	./com/myster/typedescriptionlist.txt \
	./com/properties/Myster.properties \
	./com/properties/Myster_ja.properties

#######

.PHONY : all clean test

CLASSFILES = $(patsubst %.java,$(DESTINATION)/%.class,$(SRCFILES))

empty:=
space:= $(empty) $(empty)
LIBRARYPATHS = ${subst $(space),:,${LIBRARIES}}

all : ${DESTINATION} $(CLASSFILES)

clean : 
	\rm $(CLASSFILES); find $(DESTINATION) -name \*.class -exec rm {} \;


$(DESTINATION)/%.class : %.java
	export CLASSPATH=${DESTINATION}:${LIBRARYPATHS}:${CLASSPATH}; $(JAVAC) -d $(DESTINATION) $<

allSrc :
	export CLASSPATH=${DESTINATION}:${LIBRARYPATHS}:${CLASSPATH}; $(JAVAC) -d $(DESTINATION) ${SRCFILES}

test: all
	(cd ${DESTINATION}; export CLASSPATH=${LIBRARYPATHS}:.; java com.myster.Myster);


${DESTINATION}:
	mkdir -p ${DESTINATION}
	for f in ${EXTRAFILES} ${LIBRARIES}; \
		do mkdir -p ${DESTINATION}/`dirname $$f`; \
		   cp -r $$f ${DESTINATION}/$$f; \
	done

