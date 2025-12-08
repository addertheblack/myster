# myster
Myster Open Source - completely distributed p2p network

Myster is a completely distributed p2p client in the same style as Kaazaaaaaaaa or Gnutella (old school). An important difference between
Myster and the rest is that Myster gains its scalability via virutal overlay networks that segment the giant p2p network by
interrest. Withing each sub-network the network self optimizes to make searches fast and to make sure the traffic doesn't spill over
to everyone else. So, like, people who are interrested in videos don't get searches for pictures or music.

Myster Open Source was started in about 1999 during the age of Java AWT. During this time it actually got fairly popular and was 
downloaded over a million times. Then I realized there was a huge amount of piracy on the network (shocking! sigh) and so I didn't try 
to monitize it. Then I ran out of time because of ... umm.. life happened.. so I abandoned it.

I've put Myster on git-hub so that the code is accessible for those curious or those who would like to pilfer it's treasures.

In theory Myster allows you to build you own p2p network. Essentially you'd create a p2p network specifically for your interrests 
and you'd have some sort of access control on top of that so you can decide who joins. There are a few things that need to be 
changed to get it to work but if anyone is interrested in making it a possibility I'm fine with explaining how to do it and 
contributing what meager time I can to the effort.

There's an eclipse project setup already.

The ant build might not work correctly due to code rot but it should build a self contained, runnable jar. So you can just 

"java -jar XXX.jar"

Myster without worrying about stuff like classpaths or library dependencies.

The project setup is simple. It's another maven project. The targets are in the pom file. Should be easy enough to poke around in.
