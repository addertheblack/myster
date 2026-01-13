
1. # **How to Write Good Comments**

I wrote this document because I kept having deja-vu when reading comments left by other developers in source code. The things I wanted to know weren't there and what was there was either obvious, wrong or useless.

The vibe I was getting from other developers on my team was that commenting code was completely pointless. I knew that this wasn't true because I relied on Sun's API java doc comments and these were very useful indeed.

I decided to figure out what separated a useful comment from a useless one. I knew that I often felt blindsided when trying to figure out what a piece of code did and I figured that the patterns I was seeing were hinting that there was a small list of things that actually needed to be documented. If these critical things could be enumerated, I could create a checklist that would allow other developers to know how and what to document in their code.

This document is that checklist. I've tried to include examples and justifications as well.

1. ## ***Intro***

   1. ### **Scope**

Everything written here is from a Java perspective. While I think that most of the things written here apply to multiple programming languages I haven't really made an effort to try and transfer over anything to any other language.

1. I chose Java because of three reasons:  
2. Java is a very widely used language.  
3. Java already have a set of conventions to allow programmers to write comments in their code.  
4. I use Java quite a lot and can be very sure that the advice I give with respect to Java is correct and useful.

I wouldn't feel comfortable creating such a treatise for any other language.

I'm not going to explain how to use Java Doc. Sun already has an online manual for how to use Javadoc and here is:

http://java.sun.com/j2se/javadoc/writingdoccomments/

I'm also not going to follow all the javadoc conventions. I mention this specifically because I don't want to get into an argument about how to format these comments. I'm far more concerned with the contents of these comments than what they look like.

2. ### **Sections**

I put this document up into three sections. These three sections correspond roughly to three types of code comments.

The first is the "method" comment which looks like this

/\*\*  
\* This is a method comment. Method comments don't usually contain  
\* arbitrary words like rabbit cat elephant zergling and toenail.  
\*  
\* @param preferences this comment would normally document this parameter.  
\*/  
public static void releaseUserPreferences( UserPreferences preferences )

The second is the class comment which appears at the start of every class like this:

/\*\*  
\* This class needs a better class comment than this. If I was feeling  
\* creative I'd add some sort of clever placeholder.  
\*/  
public class UserPreferences

The last is the classic inline code comment which looks like this:

int i \= 0; //this is an inline comment

or like this:

/\*  
\* This is a multi-line inline comment.  
\* See BZ12345.  
\*/  
int i \= 0;

I'm not going to deal with documentation you might find on the wiki, or stuff that would go into a bugzilla slip. That stuff is your design or your requirements.I’m also not talking about package level comments.

In summary only inline, method and class level documentation are covered.

3. ### **Why Comment**

   4. 

Writing documentation helps get new people up to speed, helps the author(s) of the code remember what they did, takes notes of important gotchas and workarounds and can even help improve the design of the code.

Documentation allows any other programmers that need to interact with the code to avoid having to read it before using it. It also provides other programmers with the benefit of the experience of the original programmer since all important gotchas and surprises will be documented. How much time have you spent fixing a bug caused by a method that mutated a list or unexpectedly returned a null reference?

Documenting your code becomes more important as the number of programmers on a project increases or their opportunities for direct communication decrease. It quickly goes from being a “nice to have” to being essential as the number of people touching the code increases past around 4-5. At about 4-5 people  it becomes difficult for every member of the team to keep up to date with what every other member is doing. When this happens documentation allows a developer, new to a section of code, find their way around quicker.

Documented code is not only a good thing for the other programmers, but the process of writing the code's documentation can improve code quality. Simply going through the "documentation checklist" (below) can make you realize just how many gotchas your code has. It can also help shine a light on complex areas and suggest simpler solutions. In effect, writing the documentation can act as a form of self code review. I've got into the habit of documenting my code immediately before code reviews for this very reason.

Writing the documentation first, before the code, can be even more beneficial. Recently there's been a growing movement advocating the writing of unit tests before writing the code. It's believed that a big chunk of the benefits of this technique come down to two things: the first is that it encourages the developer to write smaller modules, which can be tested more easily. The second is that it forces the programmer to think about the contract for the method (aka the method's interface) before the implementation. Writing documentation before the units test and writing the unit tests before the code can enhance the effectiveness of both.

5. ### **How To Document**

Good documentation should:

1. Be direct  
2. Be brief  
3. Be useful

The best code documentation is the code. You should always try to write code that doesn't need documentation. Many programmers already strive for this goal but usually fall short and don’t notice. The key thing to remember is that this goal, while noble, is way harder than it sounds. Being honest with yourself is difficult because you already know how everything works and can’t really experience what someone would go through on first exposure. The checklist will help you realize some of the things a new reader will find surprising but if you really want to write excellent, easy to understand code you’ll need to encourage interactions with others that maximize the likelihood of feedback. Every question someone asks you is an opportunity to change your code so the question is answered. Every misunderstanding they have is an opportunity for baking the clarification into the code.

We do mostly in person, real time code reviews here. When reviewing code, every time I point out some special case in code that needs to be documented I will also try and point out the ways in which you can restructure your code to make the comment unnecessary. My attitude is make sure your code doesn’t need documentation or document whatever is still confusing. Most of the time you should change your code so that it doesn't require documentation but sometimes the contortions you need to go through in order to do that are not worth the bother.

2. ## 

   3. ## ***Documenting Methods***

Anyone who wants to use one of your functions will want to know what it *does* more than they want to know how it's \*implemented\*. The rule I use is that an ideally documented method should explain how a method works to the extent that another programmer could re-implement the method using only the method signature and your method documentation. The rationale for this is that you are documenting your method’s specification\!

The problem with this is that, theoretically, this can entail writing a large amount of documentation. In practice, however, users of your method will tend to bring with them assumptions about what it does, how it's implemented and how its parameters work. So long as these assumptions aren't violated they can prove to be a useful time-saving device for both the method writer and method user. However, if your function violates one of these assumptions the user of your method could spend a great deal of time trying to understand which assumption was wrong.

What this all boils down to is you can skip documenting things that your user will assume anyway. It's the counter-intuitive or "bizarre" aspects of your method that need documenting because they are the ones that require time or trial and error to be understood properly. 

Here's a list of the more common assumptions and “surprises” requiring documentation for methods as well as suggestions on how to structure your code in order to avoid a design that introduces the surprise in the first place..

1. ### **Implicit Types**

An implicit type is a data type overlaid with additional, implicit semantic meaning.

Implicit types are very common. Here are a few examples you run into everyday:

1. A file path expressed as a String.  
2. A time expressed as a long.  
3. An object id expressed as an integer.  
4. A byte array of little endian, 16-bit, 44.1 Khz sound samples. (how many times have I seen that\!)

These things could be expressed as classes. In our examples above these classes could be called:

1. File  
2. Date  
3. ObjectID  
4. AudioSample

respectively.

Sometimes programmers will be surprised to learn that something is an implicit type. They really shouldn't. Variables gain additional meaning all the time, we don't create special classes for each different shade of meaning because we'd end up with an infinity of classes. Consider the humble file path:

subDirectory/  
subDirectly/filename.txt  
filename.txt  
/absolute/path/subdirectory/  
/absolute/path/subdirectory/filename.txt  
/absolute//with/redundant/slashes/  
sub/directory/without/a/trailing/slash  
/absolutely//../weird/path/filename.txt

Which sort of paths 

* Can you tell point to the same file with typical string comparison?   
* Can you append in a way of makes sense?   
* Are canonical representations of a file / directory?   
* Require interpretation before they get to the filing system?  
* Are absolute paths vs relative paths?

Programmers using your function need to know what the type of the variables are. Consider the following function:

	public static void sendBirthdayEmail(String p, String a);

It's not possible to tell what this function is expecting. Most programmers aren't this bad, they might produce this instead:

	public static void sendBirthdayEmail(String person, String address);

This is better but just what the heck is “person” and what is supposed to go into address? Let's try being more clear:

    public static void sendBirthdayEmail(String userLogin, String addressOfParty);

Oh\! It's the user's login and the address of the place where the party is to take place. Handily “userLogin” is a well defined implicit type in our program so that's not a problem. I'm not sure what address is, though? Is it going to be read by a machine, maybe google maps? Let's add some more documentation:

    /\*\*  
     \* @param userLogin of the user to send the invite on behalf of  
     \* @param addressOfParty formatted as would appear on an envelope   
     \*          eg: “123 Fake Street\\nSpringField, IL\\n90210”  
     \*/  
    public static void sendBirthdayEmail(String userLogin, String addressOfParty);

For every implicit type there should be a definition of how that type should be formatted. If this type is within the context of the application then you can put it in a class comment somewhere and provide a link to it to avoid having to copy and paste the whole description every time.

Documenting the implicit type of a parameter explicitly in the comments of a function is often unnecessary if the parameter's name is the implicit type and that type is well known enough. For example, for a time variable encoded in a long you could name the parameter "currentTime" or "timeToNextEvent", or an int representing a column index in a Jtable's model columModelIndex.

Even in such cases, however, it's still sometimes a good idea to define, in detail, the format of the string/int/long/Map/List/whatever somewhere and provide references to it where-ever it's used. There are two reasons why this is a good idea.

1. Whoever is reading your code probably parachuted into it right in the middle and will be trying to figure out what is meant by a "seriesDataModel". Consider our example of “address” and “person” in the example above. The method name implied a very different meaning then we would have known had we looked at more code beforehand.  
2. If your implicit type has a subtlety, a maintainer might not pick up on it. For example, times expressed and a long that is always assumed to be in the GMT time zone and not the local time zone.

Adding a link to a definition of the implicit kept in a class file or method can be done using javadoc's "@see". This allows Eclipse users to rapidly link to the right class file or view the javadoc comment for the linked-to class or method using the "javadoc" tab.

The best way of dealing with implicit types is to make a class for it and pass objects of that class around instead of the implicit type. I've been amazed how many times developers simply don't even consider doing this. Not only does your code become clearer but the compiler will type check it for you. Favor explicit types over implicit types.

2. ### **Magic Values**

These are values that when passed into a function are treated as special. They are usually used to signal that a parameter is not needed in certain contexts or that the function to switch modes and do something other than what you might expect. A typical example is a method that saves a file and takes a "path" parameter, but pops up a save dialog if the path is null. Another example is java.lanf.Object.wait(int waitTime): If waitTime is 0, it waits forever.

All magic values need to be mentioned in the documentation. 

Consider the practice of using nulls as magic values. In most cases, passing a null to a function will result in a NullPointerException, so if your method actually allows for an argument to be null you need to document it and mention the special meaning of a null parameter. I remember spending an hour trying to figure out how to stop Java's LayoutManagers from rearranging components after I'd carefully placed them. It turns out that what you need to do is call component.setLayoutManager(null). Argh\!

You can also make the caller's life easier, and your documentation simpler, by putting magic values into constants and then linking to them in the parameter's documentation

Here's an example of this approach from our FileUtilities class:

\[...\]  
/\*\* For use with {@link FileUtilities\#saveToFile(String, File)} \*/  
public static final File SHOW\_SAVE\_DIALOG \= null;  
\[...snip...\]  
/\*\*  
 \* Saves the contents of a String to a file in UTF-8 format.  
 \*  
 \* @param stringToSave  
 \* @param destination to save to or {@link FileUtilities\#SHOW\_SAVE\_DIALOG}  
 \* @return success true if successful false otherwise  
 \*/  
public static boolean saveToFile( String stringToSave, File destination ){  
 \[...\]

Using the constant's name to describe the use of the magic value helps make uses of the function easier to follow. Notice that we've linked to the constant inside the javadoc comment? Eclipse has macros to help with this and supports ctrl-space completion for links so it's not as painful as you may think. You can also use F3 (jump to declaration) on a link to jump to the constant's declaration which can be a lot of fun. Sometimes I jump around using links in comments all day just to remind myself of how much I like doing this. Afterwards I go play SimCity knowing that I've had a good, productive day.

If we take the perspective of calling this function, it looks really nice too:

FileUtilities.saveToFile(“I’ma going inna file\!”, FileUtilities.SHOW\_SAVE\_DIALOG);

vs

FileUtilities.saveToFile(“I’ma going inna file\!”, null);

Another good way to avoid having to create constants for magic values is to overload the method with a version that doesn't take that parameter. For example, in the above case we could have also create a second function like this:

/\*\*  
 \* Saves the contents of a String to a file in UTF-8 format. Shows a dialog box  
 \* that asks the user where to save the string information.  
 \* @return success true if successful false otherwise  
 \*/  
public static boolean saveToFile( String stringToSave ){  
 \[...\]

or maybe we can make that event tighter and ditch the overloading:

/\*\*  
 \* Saves the contents of a String to a file in UTF-8 format.  
 \* @return success true if successful false otherwise  
 \*/  
public static boolean saveToFileWithSaveDialog( String stringToSave ){

**Magic Constants**

I once saw this code

new DbConnection(..., 60000);

and I wondered to myself: “What is this 60000 here?”. After figuring it out I ended up with:

// 60000 is the number of ms we wait before polling the local DB in case the impl doesn’t fire events  
new DbConnection(..., 60000);

Then I thought to myself, wait, I should use a constant::

// Poll the local database to recover from an implementation that doesn’t fire events  
private static final int POLLING\_INTERVAL\_MS \= 60 \* 1000;

Notice that most of the explanation becomes the name of the constant. Also notice we include the units in the constant name. This usually covers everything and you don’t need a comment. In this case, I wanted to provide a little more context because a “Polling interval” within the context of a DB was still too ambiguous.

3. ### **Dependencies Between Parameters**

Consider a method that ignores the second and third parameters when the first parameter is null.  Such dependencies should be spelled out \-- ''every combination'' \-- so that the user can know all the valid combinations of parameters and what exactly they do. Take special note of magic values that trigger different behaviors. Passing null as one of the parameters is commonly used to signal some special case but I've also seen things like empty Lists and int arguments with a negative value.

As an example of what I'm talking about consider the following function:

public static void loadImages( ImageStack stack, File destination, String imageId);

This function has two modes. The first mode is to download a stack of images to the destination directory. To invoke this you call the function like this:

loadImages(stackOfImages, directory, null);

The second mode is to download a single image to a file location and filename represented by the destination. You call it like this:

loadImages(stackOfImages, fileLocation, “someId”);

This function has a dependency between the imageId and the stack: the imageId must represent an image in the stackOfImages. 

There is a second dependency in that the implicit type of the destination argument is either a file location or a directory depending on whether the imageId is null or not.

In one of the programs I was writing, we had a function to list this. If the imageID was null then all the images in the ImageStack would be copied into the destinationDirectory. If the imageId was not null, images would be loaded until the image for the imageId was loaded, then the images would stop being loaded and the image represented by the imageId would be moved to the fileLocation. Essentially, this method used a system that didn't allow you to specify which image you wanted but did allow you to stop receiving images once you got the one you wanted.

The key to avoiding having to document all this is to split the method so that the two resultant methods have a simpler interface. Having to spell out the dependencies between parameters is a good example of how writing documentation can help you realize just how crazy your function's interface really is. A few times in my career I've been trying to explain how the multiple parameters of one of my functions change its behavior when I suddenly realize that I don't even fully understand what will happen under certain conditions. As a result I've split that one function into 2 or 3\.

One of the most cited reasons programmers create these kinds of highly complex methods is to make code reuse easier. There may not be any other way to avoid code duplication\! However, having a function with a complex interface can be worse than code duplication because it makes it harder to understand, test, use and refactor the function. If a complex-interface-function is unavoidable, a good compromise is to make it private and hide it behind two or more wrapping functions. Doing this at least narrows the number of ways your complex method is called and makes it easier to call. Make sure, however, that you comment on this private function as being internal and for code reuse purposes only. If you don't you run the risk of some madman making it public and using it directly later. If this happens others might make use of its edge cases and you'll end up with a maintainability nightmare. This has happened to me once or twice already and it's why I've started to gravitate towards allowing some code duplication over reuse functions with messy interfaces.

If you do create a function with a messy interface, even if it's marked private, internal-to-the-object use only, it's still an excellent idea to document how all these parameters modify the private function's behavior. Someone may have to actually maintain it at some point. You don't want them to hunt you down later, do you? They might have an axe.

4. ### **Parameter Modifications / Retention**

Does the function modify or retain a parameter? This only applies to objects that are mutable. If a function modifies or retains an object, it's essential that this be documented. It's a very common bug for client code to call a function assuming that the function is only reading the parameter, or copying what it needs out of an object, only to find out that this is not the case. These bugs can be very difficult to track down since they tend to occur only sporadically and they may manifest far from where the erroneous assumption occurred.

It's also very common to trigger errors by mistakenly passing an immutable list to a method that looks like it shouldn't modify the list.

The way to avoid having to document this is to either:

Make it painfully obvious that this is what the function is doing eg: sort(list).  
Avoid retaining the passed object or returning your internal object (if the object is mutable). Make a copy\! Not doing so can actually be an encapsulation violation.  
Design to use immutable object \*types\* (like in google collections) wherever possible. They don't have this problem.

At this point it's probably a good idea to mention that methods that mark their parameters as final aren't promising that they aren't modifying the parameter. With objects in Java, final only means the reference cannot be modified. (In other words, declaring parameters "final" is all-but-useless.) Sure there are people out there who insist that things like final or constant when applied to a variable declaration are meant to signal the intent that the variable is supposed to not change (like the guy who implemented const in javascript, apparently) but these people are wrong. If it’s not compiler enforced then it’s not what it does and if it’s not what it does then it’s not what it means.

5. ### **Memory / Resources**

Java has it much easier than C++. In Java we don't need to document who is responsible for allocating what and how and who should deallocate, etc. We do need to worry about some kinds of resources.

Resources that require explicit deallocation / closing need documenting.  
Methods that take a large amount of memory need documenting.

Ways to avoid documenting include:

* Avoid things that need explicit deallocation / closing.  
* Provide your own checked exceptions that deal with out-of-resource conditions.  
* Find a way of using far less memory.

  6. ### **Performance Constraints**

Generally there are two sorts of things you need to document when it comes to performance. The first is if your method is blocking (on any IO or otherwise) and the second thing you need to worry about is any O(n) or worse operations.

When writing your code it's a good thing to remember that waiting on a lock on thread that could block on I/O is just as bad as blocking on the I/O operation itself. I've actually seen code that goes to extraordinary lengths to avoid doing I/O on the event thread only to eventually block on a lock that is waiting for some piece of I/O to finish.

Writing code that interacts with the user can be viewed as a form of soft real-time programming. This is why programmers working with a GUI are interested in knowing approximately how long a method will take to run. This is also why it's not a good idea to massively change speed constraints of a method's implementation. For example, do not replace an implementation that takes 1-10 seconds with an implementation that can take a minute or two. While it might be ok to lock the GUI and use an hourglass indicator for an operation that could take 10 seconds, it's not ok to do that for an operation that takes a minute or two.

It's usually not necessary to note a function's speed constraints if 1\) the time a method takes is minimal (although minimal is context sensitive) 2\) the method has a method signature that implies IO is taking place like "writeArray( InputStream in ) throws IOException" 3\) The method has a signature that implies in big O time like an iteration over a list "copy( src, dst )".

In general "sort" methods are assumed to be O(n log(n)), and iteration methods are assumed to be O(n). Things like insertions don't have a generally accepted default time, so it's a good idea to mention the worst case runtime.

Note: If you find yourself having to document the performance constraints of a constructor stop immediately and fix the constructor. It is not nice to have either blocking constructors or CPU-heavy constructors. The standard way of avoiding this is to use an init() method that does the heavy work. This method has the advantage that the object instance on which you called init() can be un-blocked by calling the equivalent of “close()” on another thread. Another possibility is to use a static construction method or factory to build the object.

7. ### **Method Order**

Some objects have methods that can only be called in some order. A widely used example of this is the init() method I just mentioned. Every state-dependent method needs to mention what state or which methods need to be called (if any) before they themselves can be called.

It's best to avoid having two functions in an object that need to be called in a certain order. Always ask yourself if it's possible to create a utility or meta method that will call the methods in the right order itself, possibly with parameters to decide exactly which methods to call. It's very easy to fall into the trap of building a state-machine implementation of something while thinking what you're building a flexible framework, and then later realize that your state machine has so many invalid transitions that it's better to forget the generic framework and build an object custom made for the problem you're trying to solve.

If an object has an important state that causes its various methods to change their return value or meaning (like init()) it should also be documented in the class comments also. For example, if a window has a "disabled state" or a Socket has a "connected/disconnected" state or a Bucket object has a "filled" state, these states should be mentioned in the class comments as well as the methods whose behaviour is modified by this state.

8. ### **Return Values**

The checklist for return values is similar to that of parameters \-- but with fewer opportunities for self documenting code, since you can't name the return value like you can a parameter. Basically your documentation should mention if your function returns:

  \* null  
  \* immutable Sets, Lists, Maps.  
  \* any implicit types (e.g. strings that are really paths)  
  \* an internally used data structure (this violates encapsulation)  
  \* looks like a pure static function or inspector and has side effects (getFoo() also deletes it from the data structure?)

NOTE: By convention, methods that are named "getXYZ()" are presumed not to have side effects. That is they are assumed to be simple inspectors. If your getter has a side effect, rename it. For example, don't have a method that gets a counter's value and increments it called getValue() or getIncrement(). Call the method increment() instead. The fact that a method modifies a state is more important than the fact it returns some representation of the state.

NOTE 2: Many people tend to over-use "get" and use it for all methods that return something. Alternatives to "get" include "extract" (for a method that returns something from inside an argument), "build" or "create" (for factories), "compute" (for a value that is computed from other, publicly inspectable properties of the object) and "summon" (for demoniacally inspired non-properties). We don’t do DB queries here but I guess you could use “query” for queries.. Just don’t use “get” for anything that’s not a property otherwise confusion might result when you call a getSomething() method in a loop and the dang thing is doing some io each time.

(Note from 2024 Andrew, we were making a desktop application. IO was always a landmine because any IO could block the event dispatch thread which would freeze the UI. Tracking down stray IO calls was the bane of our existence. Ideally we’d have a big neon sign that said WARNING\! This is not a normal getter, it does IO\!)

NOTE 3: A common error is to write a method that returns mutable Lists, Sets and Maps but also returns the static empty variants (eg: Collections.EMPTY\_LIST) in special cases. This can cause subtle errors since the caller may be modifying the returned collections and the static empty variants are \*immutable\*.

9. ### **Exceptions**

Every single exception a method can potentially throw is part of the function's interface. Well, except runtime exceptions and errors. Generally you don't need to mention exceptions that can only happen due to programmer errors. For example, you don't need to mention "IllegalStateExceptions" because your function's documentation should already mention that the conditions that give rise to that exception are forbidden and it’s your own fault for calling a method with nonsense. 

Java's checked exceptions are quite helpful because you don't have to worry about forgetting to document the fact you throw an exception (well a checked one anyway). The only thing you need to worry about is about documenting the conditions under which a method throws an exception. For every exception that could conceivably be handled differently, there is usually a different exception type associated with it. Your method should mention, for each exception type, what sort of circumstances cause the exception to be thrown.

If a function throws an exception, programmers will assume that whatever state the function was modifying will be rolled back or at the very least the state will still be well defined. That is, a function that throws an exception will not screw up the state of the object/class/app/module it's associated with. It must be documented what new state the object/class/app/module is in after the exception is thrown if it's not obvious.

10. ### **Accidental Behavior**

Sometimes you will find yourself implementing an interface or method and realize that your particular implementation actually does more than required. For example, your method might happen to always return a sorted list, when it's actually not guaranteed. Another example is that listeners are always called in some determinate order when this is not guaranteed by the interface. In these cases it's a good idea to mention that users of your code should not assume that aspect since it is an accident of implementation. Murphy's law of unintended dependencies says they will anyway but at least you'll have the moral high ground.

A way of avoiding this, at least in this particular example, is to use a Set instead of a List. Sets are not ordered unless an order is explicitly specified.

Another class of accidental behaviours is a deterministic undefined behaviour. For example, an implementation of squareRoot() might always return 0 for negative input even though the method behaviour is undefined in that case. In cases like these, it's usually best to throw an exception about an illegal argument so that users don't build dependencies on a particular behaviour. If you don't want to do that or can't then you should document the domain of valid inputs for your method.

11. ### **Threading**

Threading, like performance and memory, tends to be a system wide phenomenon, touching all aspects of an application rather than something that can be dealt with just by a method. There are, however, a few guidelines to follow.

If a method is synchronized, people will assume that the object is thread safe with regard to that method. If the actual situation is more complex than that due to things like the locking done by the object itself or the fact that the object's implementation may be shared, then this should be mentioned and a link to the application's or framework's threading documentation provided.

If a method is part of a GUI component or otherwise highly connected to the GUI (manipulates or interacts with GUI components), the default assumption will be that it's probably not thread safe and should be called from the event thread. If you have a component that doesn't look like it's touching any GUI widgets but it's relying on being used only from the event thread then you need to document this.

If a method is not synchronized it's not thread safe ... unless it's a pure static method (which is a static method that does not have any state) or a method that otherwise doesn't modify a state (like a getter on an immutable object).

Good multi-threaded design and documenting said design is beyond the scope of this article. The best bet is to spell out how threading issues are resolved in your project in a separate document and link to it from either class or method comments.

4. ## 

   5. ## ***Class Documentation***

Class documentation is the highest level of documentation you can get without jumping to package level comments or wiki pages. If the general guideline for method documentation was that it should allow a clean room implementation then the equivalent rule for class comments would go like this:

The class comments should have enough information so that someone coming to your design knows what the responsibilities for the class are to the extent that any new functionality has an obvious home.

In practice, though, the class comments end up being a mixture of miscellaneous pieces of information that don't really fit anywhere else. This is partly because, as the highest level of code comments, it tends to acquire application or module level documentation of different types and partly because there's more to using a class than simply knowing what its responsibilities are. The things that should be in an architecture or package level document will not be touched on here. As for the rest, I'll cover that below. But first, let's look into a few techniques for writing a useful description of responsibilities for a class.

1. ### **Responsibilities**

Here is an example class comment that actually exists in our InteleViewer product:

/\*\*  
 \* A SeriesReader is used to provide series data (image and header) to  
 \* the {@link Viewer.StudyInfo.Series} object.  Subclasses specialize according   
 \* to the data source, e.g. wavelet server, on-disk DICOM files, etc.  
 \*  
 \* Responsibilities of this class include:  
 \*  \- partitioning the images of the series into datasets  
 \*  \- creation of Dataset objects  
 \*  \- handle remapping of indices (see below), or provide a remapping object  
 \*  \- provides access to some header data, with defaults for any missing values  
 \*  \- hold the SeriesHeader object; this must be provided by subclass  
 \*    shortly after construction, via setSeriesHeader()  
 \*/  
public abstract class SeriesReader

How did we decide what to put in this comment? 

Classes contain two things: data and methods. In order to write up a list of responsibilities, ask yourself two questions. What does each one of my methods do? What does the data store? Your class is responsible for doing what it's methods do and for keeping track of what its data members store. Simply take the list of responsibilities you generated, remove whatever should be considered an implementation detail and write a comment expressing this. In the example above we chose to make the list of responsibilities a bullet list but it doesn't have to be.

In a similar way to how we'd normally not put the callers of a method in the method's documentation we'd normally not put the users of a class in the class comments. However, you'll notice that in the example we've mentioned that the "Series" class uses this one (\!SeriesReader). The distinction between architecture and responsibilities is tricky because the responsibilities of a class are always best explained within the context of the architecture it's operating in. In our design the \!SeriesReader is an interface (conceptually) or abstract class (implementionally) closely associated with the Series object in that its responsibility is to provide data for the Series. As a result, not pointing this out in the \!SeriesReader's class documentation would be a bad thing. 

If you are unsure about whether some piece of information is at the wrong level to put into a class comment consider that putting architectural details in a class is not evil... or at least not very evil. At worse it can tie a (potentially?) generic class to a specific user class(es) or configuration in documentation only. Given that documentation is the easiest thing in the world to refactor and that the extra detail could actually be useful when trying to make a class generic, worrying about it is pretty close to pointless.

2. ### **Data Classes**

Some classes are primarily responsible for representing some piece of information. I like to call these classes "data classes". Examples of data classes would be the java String, File and Date classes. What these classes are supposed to represent should be clearly stated. I've seen quite a few cases where a data class diverges from its primary, tightly defined purpose to become a container of convenience because it wasn't clear what exactly the class was trying to represent. Explaining exactly what a data class represents is the equivalent of explaining its responsibilities.

Data classes that are immutable should have a comment mentioning this fact. This is very important because,  for example, immutable objects can be used in another object's internal representation without worrying about having to copy it every time it crosses the object's boundary in order to maintain encapsulation. If the next coder to maintain your code doesn't realize that a class is supposed to be immutable, bizarre, hard to track down errors might result.

As a matter of good, forward-looking code practices I would also suggest trying to make data classes immutable wherever possible. Immutable object have good properties too numerous and subtle to mention here. Their good properties easily outweigh the bad to the point where I would even submit that immutable data classes should be the default and that the mutable variants should only be used when absolutely necessary.

http://en.wikipedia.org/wiki/Immutable\_object

Marking a data class as final is a good idea since a data class with multiple sub-classes is a bit weird. Data classes represent data and don't have abstract methods/actions as a matter of good practice. As a result of this, data classes with sub classes is a bit of a code smell. If the data class has an inheritance hierarchy, you'll probably want to 1\) consider if a composition of multiple data classes is more appropriate or 2\) extract the abstract actions into their own object/interface hierarchy.

3. ### **Utility Classes**

"Utility classes" is the name we give to classes which are not meant to spawn objects and are filled with generally useful static methods. Java's built in Math class is a good example of this. When making one of these classes be sure to mention what sort of functions belong in these classes. Putting a new static, utility method in the wrong file is a common, annoying error.

An alternative to having to mark all these classes is to make the default constructor private and/or using a naming convention for all utilities classes. At Intelerad on the InteleViewer team we have been trending towards using the following conventions... Say we have class "Foo". Class Foo is getting a bit too big. Class Foo has methods in it that add capabilities that can be implemented using Foo's public interface. We make these methods static, taking Foo as the first parameter and move these methods into a class called "FooUtilities". We also do this with internal functions that do something that can be made static, are self contained and has an easy to follow interface. It's been our experience that with some kinds of objects half the code falls into these categories. The \!FooUtilities class contains methods that can be easily unit tested, can be documented and (in the case of none-Foo specific functions) can be generalized and moved to libraries. As an added bonus, the purpose of the class is obvious since it follows a convention so the class comment tends to be very small if there is one at all. I'll admit that this style is a bit idiosyncratic but it's been serving us very well.

4. ### **Misc.**

Once you've spelled out the responsibilities of a class, the rest is gravy. Here are a few things that you should consider documenting that fall into miscellaneous category.

 1.How should this class be used? (With, possibly an example)  
 a)Should it be extended via inheritance? If an abstract class then is the any state that needs to be updated (fireSelectionUpdated())?  
 b)In combination with some other class?  
 2.The design philosophy employed by this class. State machine?   
 3.Links to architecture documentation  
 

6. ## 

   7. ## ***Inline Comments***

Writing truly superb inline comments requires the programmatic version of lucid dreaming. You not only have to worry about the code you're writing but you also have to keep track of whether what you're currently writing will surprise someone who does not have the detailed knowledge you possess. You have to be hopelessly involved in the moment as well maintaining a perspective of an uninvolved third party. This is very hard to do. In practice, most programmers will just use a series of triggers to make guesses as to where there code crosses the line between sane and baffling. 

Before I get to explaining the one or two triggers I use I'd like to point out that, in general, good code should not need many inline comments. If you do need an inline comment your first reaction should be is there any way of making this clearer.. For example:

If you find yourself commenting on a variable name, consider using the comment (or some concise variant) as the variable name. I've actually seen things like 

int r;// count of all rabbits

Agh\! What's wrong with rabbitCount?

The same sort of thing applied to class and method names. The gist of what the function does or what the class is responsible for should be obvious from the function name. Don't write a function name in some sort of abbreviated form then go and spell out what the abbreviation means in a comment.

A word on code reviews: Having your code reviewed by someone is a great way of finding places where your code is counter intuitive. Make a note of where your reviewer pauses for a long time or simply looks at a piece of code and mutters "WTF?".. These are signs you've found a spot that can benefit from an inline comment. Be sure to ask what's confusing them so you know exactly what needs to be in the comment.

Here’s the same sort of thing but for methods

// calculate the divergence of the solar axis  
\[ … snip 10 lines of stuff ..\]

// send divergence to mars probe  
\[... snip 12 lines of stuff …\]

If you create a function of these blobs of code you can give them a descriptive name.. calculateDivergence(), sendToProbe(). Great\! Now delete the comments\!

1. ### **Bug Fixes**

Many bugs that get out into the field do not occur because of typos. These bugs occur because some special case was forgotten and it resulted in a reference leak or null pointer or  forgetting to do something like update the screen or notify some piece of code. If a bug has been filed and you're doing code reviews at least two people have seen this code and not seen the error. Put a comment. If you have a bug tracking database you don't even need to write very much. The bug slip should contain everything that any maintainer would like to know. A summary is generally appreciated, however.

You can also use a variation of this when writing new code. If you're using unit tests or are otherwise testing your own code and you find a problem, ask yourself if an inline comment would make sense.

These types of inline comments can really help out code maintainers because they are the sorts of edges cases that are likely to break when the code is modified.

2. ### **Odd Code**

Did you need to implement code in a strange way because of some constraint of some other piece of code? Perhaps you had to..

violate encapsulation.   
use a technique to avoid calling a method with an unpleasant side effect.   
grapple with any one of the hundred or so subtle problems that come from using event dispatchers (aka the observer pattern).   
use some hyper-optimized but baffling algorithm.

Take a moment to explain why. Generally comments should have the following sections:

Here's what you (the maintainer) were/might be expecting to see: This lets your reader know that you are indeed sane and have thought about this problem.  
Here's what would happens if you did that: Deadlock, excessive repaints, explosions, death...etc..  
Who you talked to about this problem (if any): This lets your reader know that at least one other person agrees with the current state of things.  
Here's what we decided: A brief outline on how we avoided the problem..

/\*  
Normally this object would be inited() when it was repainted, but because this code is called in environments that don't have an event thread, calling repaint() tends to be a no-op which, while it doesn't repaint, also doesn't init() the object \*ever\*, which is very bad and causes unit tests to fail (sometimes). After talking this over with my teddy bear we've decided that we should call init() explicitly in the unit tests  where there's no event thread.  
\*/  
foo.init();

![][image1]

3. ### **Conclusion**

Well, that's it. I hope it's now obvious how writing comments can not only make your code more comprehensible but also improve the quality of the code itself. I've the contents of this document in super compact checklist format below. Feel free to use it to help write documentation.. although I also use it when I'm writing test cases and code to help me improve my code' quality too.

8. ## 

   9. ## ***Documentation Checklist***

This section is the advice above compressed into checklist form. it can be consulted when trying to write documentation and trying to think of anything you might have forgotten.

1. ### **Functions**

Document a function as if the goal was to allow a clean room implementation by using only the method signature and comments.

Documentation checklist:

1. Implicit types (paths, SQL, ids, maps with certain keys)  
2. Magic Values (allowed to pass null? Object.wait(0) â€“ waits forever)  
3. Dependencies Between Parameters  
4. Mutating / Retaining a parameter  
5. Memory / Resource management (resource.close())  
6. Performance Constraints ( non-obvious O(n) O(n^2) )  
7. Method Order Dependence ( ordering requirements between methods eg: object.init() )  
8. Returning null.  
9. Returning Immutable Collections (inconsistently)  
10. Returning Implicit Types  
11. Returning Internal / Retained Data Structures  
12. Static Functions with a State  
13. Exception (conditions under which each type is thrown)  
14. Accidental Behavior (aka undefined behavior. eg: item order not guaranteed)  
15. Threading (Event thread only, locking, miss-leading â€œsynchronizedâ€?)

    2. ### **Classes**

Define the responsibilities of the class to the extent that any new functionality has an obvious home.

What data is the class storing?  
What are your methods doing?

Documentation checklist:

1. What data is the class storing?  
2. What are the methods doing?  
3. Is it a data class?  
4. Is it immutable?  
5. Is it a utilities class (group of static methods) ?  
6. Examples of how this class should be used.  
7. State machine? (What are the allowed states and transitions?)  
8. Links to architecture document.

   3. ### **Inline Comments**

Document surprising code.

Good triggers:

1. Most bug fixes.  
2. Questions that come up during a code review.

Common cases:

1. violate encapsulation.   
2. use a technique to avoid calling a method with an unpleasant side effect.   
3. subtle problems that come from using event dispatchers (aka the observer pattern).   
4. use some hyper-optimized but baffling algorithm. 

A typical comment should contain:

1. We'd normally do this..  
2. But doing that would..  
3. So we talked to.. or investigated..  
4. And solved it by...  
5. The issue number if any.

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAicAAAGxCAMAAACOWeYsAAADAFBMVEV2dXCfnJC9vr/HyMkbFxgDBB4YIC+QkJJuc4N2f42xtr3q/v8tLC9/g5KHlKRbYnFOU2T//uSep7Td9PXSxbUVCAgAAAAvPEw3JR77+9/x6dNycV/R1dcYHCYdHiIgJTRTRjqxr6q/w82Ym6bu7uz29fU6NzQhIyZCQkNUU05aW1poaF2EgX36/Pqko5/d29Pg4+P///8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACGUjbsAABI0klEQVR4Xu2dCUPaSheGzyQBEhYFFDfqvtbe2q///0f01t7aulRRC4oooCwBsn1zEpAQQAHDah4VySQsOXnnzJnJLOSrCgQayVu2rftfw29N6BLr53eJZk2wIFi2Ocu2Qws4pUSshrUazqoT67b19RXLtvV4K/9ZtvcbN61vb307637rthXFsv3Dst01/1gTLFjPz4r19T8t258t269g/TirvV5j3bItiBAA8s9vYnmj9YuGIy/Wi+bNFp+rkYYf6+4mrG9geb11t+XCv7ZbYxq2m3n9Cw6bZnPQa/X891as/tSK1T4VN1MOErLsteb/GMCa6a9JYN3y17JtvdLW7SXL9ivcWLYXLldf/PFYjl+0bHfNX3fFDaZfybJf85TB/PvBsv9vw15PmdC8Zv61fuFXIIs3jT8LcPvirwWrFNUb2JLJQTn5Vh0MG5kz/1j3jiD9/cI0vONL9R/r7lfxNm6Sw4/Hvg2y7h57nTj0l6yc+vhaYe7ggDg6cegERycOneDoxKETHJ04dIKjE4dOcHTi0AmOThw6wdGJQyc4OnHoBEcnDp3g6MShExydOHSCoxOHTrC990NfyN4sTVvTTMSC3uuc20dWrDssqEpiFa7nS1PWHQ3IwCpms9jfQ2QMYUNsIWRNHDCq/HR254q5eeuOGtcSL2XaCyWrVYqcj2cec2HrrgZu5eyjJ8sUkh76SUnP36D1AJ1bpZIu+p/72F0XlatZ8/5BEgtc5Nuf96AoqcXI8Mud5LfEXTEcFqeuf8vWfXUy1oQ6/+ZV40mI/924x0qJaqr6NDZ9lztu2FljrlAALXZV3VI5VXQ17B8ky7f8CzYZJEPXSdI9V827ocBZ467OyM4SyGge4tVoEfHHureJBTmyQrMor0DohfLEXTD+Jx+rGhwO9MNHRCjD1kksVAQQcn41Rb+JfGkk5hoOeXk4T1IkkHUtzC6FloIQKlk7izezzGB4gn2dpZYHpzLou5QnfH6L3254JOk3Kb8u/UEw9PiEFgQVeZOfXvAVNSEfocqJBR6vC0nNp+++9vxirlmWTEtYTic9peP7wpM5sDjzQsa1i3InyYzAJ2Yh5n2UPPQau38b8UpWPEvJdyxPBaeVPMF/H5TMdPKBOjFP/kaWznl6bMz3eFkLQmixI1DpEokHWUhhioIGot8qW87Gi0a4EPOVjtnk0zS6wvqXMn+yms2obvlOqvANr73m7sWnR/lGznvuYq4bxojJTG/hu3yqkN/K3f0MxGZpFmELNJq6LubUttFb38H4ZMg6iQVFIKDXU0g2VA74qK35EvCs5x6vn3zoEwW6RYBDnVyTfEngWeYhNVN7g9vFEkh7xvPphA/4m5lcUZZuZiBTFAKpaWr4rCQIwFXuwrpOSosk4w56UziKRVAXMnqEPPXLJZaqAWNBwBIMFJr+lNGLRNRJcjYlaQp4fHnUb9KXLQkKy1SomKpf6vEh0/DJvixIT0VNfphteG25CKpGZO6Bz2uCzCXxLM3nVb4MMGqJfl9PZjrLotcribNQVuA+Yny9IaDHsTFr6kARqGctVquzy76lCMjZ6uiX0BXNy/nZWr3jlv4lORUyhLoOdz1omDuHzPN4n1kN0iEI0Nds6Ns3VGnRu+pO74nx/wlCIbhS0lSZuZUEfQXVx1OYFlnV41AkmTkNNOWaFmmCEcTGyAP9slRAonSFMVW1OCrfgzxvfJnQlNr4yThci/osYnltlWD1VXiWrc4LNBnW8l7qDTn6LgnIuOu7Bk2CZuUhxycVBTINGeVhnl4/v+9Og1AObl009sj55ug1XVim31cFMr+06GNAeTZ4PATksbbhpqpz4zC10nOIepPEgifVWAXOwNrCLGRyq+AKQSihj3Qt7ZiPOKeXzx2ml08veKj3r4BHDodcM5CmOgpTmZAA1RINc+4lyMgFbgYFFjN/MpItFXctr8VhDu4FekaZnH+BgdC95bxo8SfI6hzAlAbLNM8Ie/SLBX2BTfP7Dp4h6wRpGI5YodfGNR38Ss3Hn0gZyPj3gq6gbt/bHWpbKqpgErTn645Z+BltFh3PJ72eYBCLUKV92PofNTj/XMCb6tgLNJvOX93SssTSRrKIWRmA260eRyC1Sg91ayFVVn9jTDXlmkNVLhRoxcmf96RyvoZPRkhgc8XyWpoqrEZYWlKGmWmWFn7TJw3npaGAVpfjXrit+j9kLbha3xgKI6CTBo+6QA2I13FGgwxPg80w6iCqlzuRDCysVSirei40yGkw//xiQzMZ00hU4ZS6/mn9MOvwUgNWg4SIeiw3pi9M66GxF+K4pdLP28CPrmAUREspj39FHy9OPwjHM2v8A8+sNHwy4AXHErXxtdVdtNpd3tF9BvCtzmtNerHaPlgwIw9ZJ7SkeLaNWvip14vRgAY0f5ZXsCEdiUmExgMpStGUbaMZuEVvcytdyliKwY4cNN2NqITAU/XYdTlhexuCDubKByEX6tHkZHQYWaP17Wro9EQ/T/9oemSpRO2Ww8ONeQbmGVpIQjng1xo+mRLGWFlufG11F9Wt5wTr4JBZbnleoebx4EMDM/KQdSJu0zxfDTa+Z4O6X0YDwhKhpvppbHCHmEvXUvTC3UVmwuGZcunZnncf6HW+Apm5466uaNAYvuayRuuIDi2WKtf4nyppuZaoC6TKGpUmOvWnhvAEWdbmPhihE72+tPIdicx4wzOV3OJO0NByDPSvF/N9dWH8ox03fDIYGgeu8bW6e2rguvG86veeRqOBrcqQdbJWohWLgl4SJ6npyxwtX9whdB4016/Q4BBDvyQVE9yDywea180LAr+mPF/VhUusGlwBvVAeP32RuELDc2BlSOq3aHLU+1N9XF8TfIdWHKRBLELmuaJdZ9mFNWXdaSz4IfTXzYcE3uslGLegPoyalOqJ/Zn/XwoDzxPzJyP6lW58bQtanpdBvHkyhyExZJ1AlsaPU/4/x9eKOwuZRXAzUGb/fI8+QGYWN0Lyb2XauClDJRGKYDWycuMyVTDpFfbkzopefUIGQquxBxoUYtdG1TWahuDN2R1pvkHIQyj/hwr0CkXYXOxYYNNQfJJB/lfKPaIQC9zRtFHhflJDoUc9EOF58ydTqvNRNLy2hnH5IzirRuvzovrRRVr4RXNK7F/8qsNiBOITWHtEl837mVtqXW8S5mnGFPlZeg3CWZinldpg4Paheiytg4iVREZNYTFSfwMvHlQwLo6maHA1CyGBMbavaH1FEyT9qjTGy376Kl5Bj0a3MlJTsdPI1R6tpx+XyhESzMIila9nWv8AHsoMiPmnG/qF71bMn9zmtVUC1TBMb+K0nJeeZuiWhl6hqZsryEbwqw6LEYhPaBiYr7n8TJmjprvGZgmK4KN5NFhrJIMF6nfmp6kktCKtOWer8SUyX66+Qsf9x5CeTgjWMsZsHpnaIXogiQ8P2IA2TXMpvdBGrcpMw+Qx9LVrcfpNgg/0OlcIzAtpPT2L7zQvVN+a1o3Mn/wTS1H9ecNrrXNsYWhrPi8aJNdcG3U2y4v0k9ICBvRdTpZjN0PXiTzPqa5g2kOeljfw6q+5NBeTJk8KBgdrX9UZupHzhR/xSjLllDfEF8uuLfM7LJCluWCavsS/EISMgtKbYzxETlVocTD/mPIy5Gn+nmGTC0nGJ+gP+Ko51SugG8GmPtGku4VkMkQdWxWZDRIUMv0mEYYPkZKE8p27C3pIaSVC6BsE8ft6KqX8SsMnL3pJrWFo7XP9taxXf7uSV8C9V27Bv9NwXmuE8eGplhmCzoedc4XYDKRniMmFDgNCdiZonhz5hu/2NoiaVIHIJp00Y3Roe7/d2v79cL83WSfP1eu+HXLNqeDRKzbtMWw0WZbqhpGIT4aLfgvO/bJMHOA95xKdCiGZlZe7VTsg79yfKCkScGTyChhxv3N/srZmTXFowolPHDrF0YlDJzg6cXiNUbi/4zD6TF58otXuqsZqnTeSeAc2pqZS9Ff/w8RbTdV/tOfjzSSTjzQ52bAL3y7273n9fu97gyUzwxyXYS/Srcfo+3H9lMYxxAD/8k8eXi3lFCWvGH/xEJO9kfM5/MnlisZhJmTIa2UtJOUC9c7Xt9xJehZyPg5HXbRjNMYC94NgOeiZJH9yWwHtHJ/E/OGg3hnhdpsE7+HwufcbZT4he+u9UYJNvWY5RVWB1/JQzte8R4x5CM2Ze4a0ZHTGAveDSdJJyVU9H7YAwOOFxQ7Ss3BgOsnM3Qp2T6yRbVp85xajtsdL+hAsGNqQo5I+IEe5L/te6KaijsxY4H4wSe1sa/9GYPpqBeB+DiCNtwRz07D0OM1E2Dzwd1CZBSgYV7pcvUdeaCoppAyBzHJGKGJ/yhM8+j8cbLaYh7UXbxknCY4Frg7jmDwmKz4BkprjIenTAIT7CMRmJSisUSdDPJ5DHyjzHg/GFyc+UENERw83rstP9aG+ZeoXvGJUxVWtOIWWUNd6fxBXZfrWffwQMY//BbV4X/Lp/9Tb57HA5tHCk4FaZicqPgEXq4/ymNXjkfkrEP5Chq3ui9YHOlj6lP1LJDUseiQsayCJ/QuLK9XyuHQJSRy3qSPdhdQr4beHkzJhjffTQknJahjD0H+J6mppOLz1Wr3LhoNu7qJVZWpcmSidzM7gCcWeACo4fAsqYViwrGJWxfPtW6VSKf+mFzvJEl1CZb2Xs0eFTGYaMIYpMxDKP4ZownN12DQGSJNBpq+iJRwu8OmpjwVuOVp47JkonXBHQAMUGsVmGBy+hb1cK6070mtzc6lU6j5Q0IeCCe4FldGdQYxe7XCt872gQVBJ0eIkUFusMqmPL62N//1LP+i5vvQ8FrjVKOjxZ6J0gkMyEtMLBJYIdqenlVt4fKGGYkALKzF5VZy/W6FBqHAMIIrV1wTnjbEePtPQPM00/pd3U1GBpRxrOQp6/JksndzRACVFI5Tkyi1VjErDk9bFDpDZCOVOpFHoAnUbIY//Noz9UG7CkN3YqB0Vx862Huz4b7yolg7V8b9PTcPX24yCHn9eqOmNIbNqCrwqDgF0eYohOQzWNbyfYfHEv+pPgzkfVoJzv5md2GoRhPpL1pLeYkZoOaNE6A5Xo4aFa0sP7Bj1ZuXamPYJClAmy59ggEJLhnBSH8dFs7N3yXpIM3kxpN6lISTPw0qhXkFC5itzKxaZmMb/0k+6tS4u3noU9PgzWTrB6TAoGGCw+jPV7C9b+xYlO5ta/jrnhdANKBjamHfOu17oFVniaRWa1oONwT7VscAvjBYeZyZMJy70BlkMMK5wHrXMC1e5iiwRMZfKYWu9hm32yReH8phxrV1CKHCZzRrKqo4FbjdaeMyZMJ2UsG1ZjzBwwopXh5cDzjuhgafyxBQhs4S3g14vqOrjfw8YELlC9a5ibSxwy1HQY8+E6WTtHAeV609xaK5pVpPGiWeqQ3+RLHbDoWS4aZwvp97FxDLDEr5+ERrG/zK1scXY5as2Frj1KOhxZ8J0AnveLLavUub3mEq9VixXB/YibH3oL2WtuDATSgtl15686M3ifFpIOUjqVV7XnMezQh+F9Erj+N+gFgllyw+uMt7/q44FbjMKesyZrPHFDv1AenRPTZo/cegPjk4cOsHRiUMnODpx6ARHJw6d4OjE4XUyjk4cOqKbfgWHe12MOzDfdrWDo+YJD3d/WlNgfZLaykeJbnSy04VMwO75TluMeCi3aPC0Tt7aTLypNZ05tSQ0CdCRXzc6uY5aU8aRaJOClTVLQrMALfKzOEuLr2vMTp9bd2cYM7rRSTE+EUKxAYvUWvi6OsaAjRrYEb+OeY1IixNb7HaC0/7SjU6+WBNe4tCa8EZWEtYU2G7uL3Y16krWGiS2ZnpudWLPLqwurWdZ4UTsgy0Lu9FJVxxYE95KR53X9UUNXiZ3gY/bGoCnmoM/WRe8bhLgUOVXl9aaKdVcFlaLQUNGS0bvKJvt/17vF5ep8c96tqXFWVp8XWOPWtXU7WXgUF9Ui9L1qKmnM5YeC6t98ycjztHuW3yp1dwd+Tod7Yd5y/wNLE5Ms/q0nqC+qCFKRx+kV/hQPXpE1GHx1butxpvm1pjBQKwSe526tJ4v1pb2hrJQr/CheoyIqMged/Cd3qtOsBtsy4E5o0cbaZlCsWoxaFzL8r7+r6n23xbFOrakFe9VJ1dNTSRjTGshIdQX1aJ0PVd0rh4L71UnOI+SseTbRIO+yPAvdfQKH6pHj4iuovFOoqv3Wt/RI7o2g48dGsH6zru+XzzMGuuY8W51gm1TDXVUh5d4tzpx6Ip3q5MjeL9BfA+8W50Mq6FtTHm3OkGaRhA7tOPd6gRvi1lbFhzawrhFp3bo8CIKBKk/6aR1fwL5DG9oxX5/MJZ+ee+Giei1OjjebXyi4xS5nfJ+deI0yHbDu41PHLri3cYnOk1Dvhza8H7LHeyd3jzWw6E171cnDt3wfuMTHMQ3SZ0f+8v7jU9wUIJTL+4Up9xx6IT3qxO9J7U10aEN7zc+wfkAeh0q9f54v/HJaCHLucNRjpbeb7mDC+ZAypo4HORDTXratHsqkBewzIwl5xFMlJHGnTos5y9PzDrX3eHHQbqmlWaHgqwWXXlRdP0N8a7GJaLeiswYf63QFCh4QPmemofvN4L0Z4pBjjgJFFVVtcbJfECusPz7jU9GADmvEZUplnzi7N616rZVJrrPaNdR/FA+9MNhZdcDOXUn4Cv/LT6c/VUBvPcacmY9Hv2JZ7D+RFYLx3M2W6Q3VGqYxTYZbiBoqprKezSX9z/fdWDxyVuorgRkB7nj1Gy66Mu7WzqVx+kFNr9wL4Z5cY7LgjedrezcZA7OQ+nHu3Q6vWKZb476k3aK6xPy0UeZFsRdzeDVL87WAI6H1UU2L/vKsjtRCiWKBwDC/s/9rB9XqrOL64+K5NcqRAH2qHmqtxJomnIpkNiXC39hy+8XbkN/6DXRtA8eorVco3Vg8Un/CuJeSQUBsk0mHADaj3kou1X18l7e1fyrIKfchXJh+WLOeuAbSAT/5gKgEkWJrV3eeS2jUO7C5GjRJyT2YTrkoZffFWB8PMjeR2AVRWMsl8eITwZBfwviNzDoGQuwLpEvbebgQvsNB//bZASczkiAWXnjxNZODkvYNhSHeDwehe0tyFt2x+Fj/uRk8z858Qfj0x8qcwUgJoCnMmk6GGH3ymJ1IcT+0c+CuHcSMwBksOWuJrFF94XrKsDPXu24GChfPPA8MFxSXOHnbTSKXAxJkMtFIRediudyUzfhTEOhxpSeWD7z+Wrjejm1TLc9N8EpD7iCnlPxKem2li/y9RTPzpVLfdVJvsiKIknk+MzdEnM7++F4Jun1joRDuaPnzQxWJ4fiVF6LZIUg8zsi0I/2BBU3x4BrKhG11bEzlUrR5wsQnPB3asrlmtIaA1EhI6Se1o5X/6xerGKR9HvhjH6Be5e2PDu70BQmyZmp/pY72iH988ja1UlxR5g9AJmISfZy+WIkZDIMyLLk8qkgyD82xTQmXGYS9AKewb7NevVDxHX2hxj3JbBkaZwVkADZjgSWmDAjY7mX/wQe+gUSGtt2pEq/dDKwgrh3hjCC5zN7fn+vzPrT3JWx+GleH7u6Ix61agN9C3z+u/K8ai7oC+eayC+FXALwTOhcnyNJ+8+La/nycbatXNvueBua7Mr747OJZfh0/g91H9qpshwEbv66tMW2n01ssAzBqynnocj1LQuudY3BZW9lEsI7ByRp9+oiqSBrDtFdXOPUUeSyshmAQDaxZ/iJdfcGfQyn2lf/+hSfDKogfhOYh1zWxL7CaFPgFxf4uftEEvs1MOzUlEzj18i8nVbJcRJXK2dEeoLHYcVSL3ZHFjCFjxh5xcMzuOmLNIUmOn2MTwZWEL+VAd+jnT15yHyY5jkS8uolDV8M+q3HvBHt6BYn2zbuc0Ia//vf/hl90sngCuI3MISRXoV1AiwJMMFQWM8wgbaOvkcOy+JatVfNCnUWWOGRtmyweZ+y98AK4jHDr16H2LyfSMm+dJE63N2qRuaEo0VPmboUrmLLrYk+6YQLe9XFRESaSmXkr7i95PXn/c6MI1DYVX/SQD7YyZyt3VJXCfv7C8icgh32yNvLHKRPcSx4f5LSgl/1FHgIYtmmijM2tjfawzBuGHsYggFsHzgMhoxgK55e5BYwomCkXC4XaXG7uEswju2TP6EFcUGjBbEW1FtwaEHc2bIMA2UoN4z7dKdAk6q+JC4t10ytcVHgVHs+781ia4N/Kg1sHoj01+kH1ZLDStm+fo45STRiVVLZ3n/OkaRyJrhtusD2qK0F/SyIJ4EdGQTNpqa+Q8GIikk52JAr/QcyJ9tzhfsVn/SxILYNF618zdhjxu55dMHUzwxvw/SkWiU0hf/jfIC37mPsKDD62M4GpoJYPhTFbMOuEeHKmjBAcgKIsLtutLa9iaxoBCauhX667v7ppIYsbmrayNV1hkz+HG+KxrXDknWIRJfkyoZp2YKrrzWF/utEHMYNt1HHh/eVRBpVnObeVPB9v9WdCTn12N2wa6HvOkmd60WwfbG9fQyhY0ENgjoR4nG//xYn2u+RnLStB7Ac3/eO6W9ScwekEtu6Y9WwNXbEGKKfe9L7oGIX1qhKi+beroLmxjveEC/2XSV99yf5m23jvuWeP2fd957x7WKBDCgVerF7uwiHJd0ZcgsDkEmPX7Fj/Ae6Sqh/P23c8b7JlzQ9jsWb/rIk4e30rjnUu6vFPe6+xq81+qwT+BatTh4Rve7BFn1mSCPRZfC7CPqTKH6BOMRkrftCObuFdiXigG7A91knh7t4MXQPu4UGGi2wo+6NNbF7uj0tDo7kOPqTygdjlI3cfS+YrF4dZvlBdCIl87E+x7H5PQmDRb0xWb5dZXsO2QaN5WvKJXzEm6a6JiznUOrWG8iRak94+XYJnx1jzasrJEMmFRuaczujn/6E+ldap6h1yIkv6VO2jgWaqtCwW/lGa/Pfv2VzRxKL/EhlSxJFtlSmuw/Rg5oetgUD2z4GPNdb3Waeb3pF59gzkNBEp5864SAlG7Udg1hUs2bF4dNykEhX8z6oFZxJpQvko5rQcrLMe9ndbi/3N5zTFI6xA9ig6KdOQA6iO9HqnzFirW3o31pOSc3DJ8jtPaTnQdnh0sUPxavMTvHPlxtFvD6lLNWOy9GKiqRciv92FaNzBydVLxvgOLnr8AaOdJnEBimTvuZvagD0r0snOzQrMrTiw21aDxlROpv3gQYxp9IWkSC+W+guRPn8aw3/5WnE0/0FUNbw0TXYDlbdf80u4DD8i3oOyjQ6WXUBm+vWwQ4LAtqPvSU4/6TucE9UAr4VifoQeTUtUI0wWKmlovmXaLBLMwP2ae/y7grZ4YqAozuRLi+BUdOJDVYmfS13uFwOotGCiv49esWyMC4y6WTeh8N/93aoTOJxoxWkS7rUholDvcFn4N01+9ZPCfF4f3/IQiDvY0GMgNpPTfZEmMaTs60aqlrP++A2zfuQXQpBfD0+NTUFMMX00LsQayx4M7BLUgpGNgMNYUGp5BZ6Eraq30LDbNVwK81aRtMD1vM8DfautkBQpPrgO709padPtpefW20GBM5khQS78d/W2aYxUDu2fPSZepVt7Uv1hI/XYtF4FEscwBo/jUa5LqyCoLKMp91YJRfAoZ3sQGWi88r3aolSInhu+FK0iFFagzEmvmai/6A6WAd37GI9UBKeLaJXC61tkP/UnuBLawm4WEFDIxTubLgMeB0F0eYiDed9yJnnfXjCUQOaxiqGvXKMgvcjDEdMtgiGtF1aBQcAu/QDurHKHr4sLlUGbhXy6Sm7ZU18Bem3HvKvX6xfLGjV76fpRjLAFLpd/eIaJtC8ImrPwVDLPNyadl2Nn/sL65fCzZR76fR3SM+cbdWkmWOIR/Tn3EpMH+SQO9s7odWdY2atWlLkTIUV+f1Bj2K7tApOIICxbD1E7MQq+mm3n0HcFqs0Uc7Fv/TiT34uezGTnNKfphL2L9Sz03MzAzzQE3iUYOFyVf/RL421l/Vfd8Wtl9p4jh8wAYy3abiQSzcACwC3Xrhd0BOwSqWdw5atdSnhpGHeB2Kd9yGg4GhpesXoeWm7cJr37vZgFXDrd5c6twrD6sGJZwhW6cWfKOUk+rUuQLMyTZ3Be6GpQQvz5Y+Pxz68kN2SooV9fNua2hG58496+xgqBYmvna51ZRX0Jfjdu7jhlXNhqdOqRmyrVZpAf9KLTrLuriwCxgUBxS5tN5OVUx+taZ1Qu1i9oP6n6O2idUg3Q9p6+OgzdEUty8nW9GoVK6iT7it0vRDBJoZza+p4wxxsejTz7SvQxKOu7wh2wSHKJD6kkQude70mWtYD29QCV6mT3s/XYzXMeW/46NFg6nBPjlKlRCsXfr2GrK3Fr2WcZqwDq9TrxZ1aZRsPE03vOkjaf63XaKwH4tdvUwvEZKzzxZbrFsFtSxWwVgOsVotb1Yot1T8s/Gyt/HXLAZyt4xlJ2/xPVS+EqFywS6O5dtzGKmgBjHdxd0dW+ahHsh+l4Vil9/ikWg/EimCx+kUt1cBaLZDWEmvVwG7qgEjLanEtDNCvgF7567Uk7iFIsJAKSnCqd2T+d6eaJAoXNasY2aelVfAkxOqujqyiv8B42merWOm1XmxQrQfGaEUQJWyiVS0QT23qoqEOaKkC3oHRmI3n+aF1rRi3F+q1v7dX/vAyHfbedVAGLnRUO481SIq6dxFgK1bqyCroT6w143ZW0d86gD1fWlqFWqRfVWKd3v1Jd7XjJ86GmrGl+ufTapW/XnMOTnh31rtOEJmrjkzSrXLk0gOVuPjqmz67ss4qxtgk2KauY7tVrLzNn+S6irwD1GWqqS5vv1uxhoM0J33JLg6hx7wJruFb7RsNcFGQSp1m586uAAax8dYZeiBWYaxTFfcJfSkxdL0TTs44U5DctlaRs1jqrGEJMwQIjY0H034CRrG5a6vtRpRbPob/FM7OHI3vFdc7/Q+JgemExRapa2vqkMEx4J35/W7YL6BVtax9QjnCqGej05KsH/Sik5g1oRMCGG65+uRQEnrQ3z0t48IeidWfRiQUX9Q2oeSwYkRadah6kV6t0opedNIb/wMcPGpNnUwCbryytglF78Nm3KEeFoPTCTmmD64Xm5ImCBfWDuwSCro9YktXkp4ZnE7gfzRCiWKb0WjRW7+CVwnqQul+qGAL9MrOcN3JIHVCUCPykzV5mOA3alzpyj50oWh2REDYPBkfrjsZpE5gGT/M1uriW7EvzmtBEGMU61jkHjhEJQ/bD/euk5g14VUCuhO2qcyuYWdQbwMx03OcnxbKbz5dXWldNsDbbpXeddIDESO4G7FRxv2jgtZ9a6tHDtvlj22+7F0zUJ0YrjjaTffA/tPHGlggR0N39Y0ljxcrxdivfKgMVifgwsqxMTrKJt50dwpvePcrjkUia4C3W63JXYGRCdPtDdQ3WaUVA9YJfI0Re09Cv2u9bR7t0AVvLRNexYX2bZwupVtQx11b7E1WacWgdQL7gnfIVbyBckL/dlsVbXFjGsHO6LueX2XgOnlnHOCdnlYz4RTjHTaJuCAeG3YU24+7pQOma5c8YB59tLLcos2348mBXa4Wr34N260y9v6kq151TWBG7W81PdL1bV47eJtVWjD2OnkbAzj93yxrjDcfa8a+3Bl5vnRRxowuA8hQ/cXOyt/kYLtVxl4nDgOBydof84wZLWcadnhGxoGq79yftJ1p2KGRSdFJxprgAHZaZVJ04tBfHJ04dIKjE4dOeOftbLtlfaGxvpK7NpZcRfA+gb0N+YWA2vGQ9zdgq04sFrHXIH2xSNsZqW3kfBYnMekbBZizJvUBW3UyHItkpqwpo8XGU3Hj5U5z2DOye7U+1MYLtHxzm61iq046skj3BnnFIqNO4ElgX3at2Gu4e6p9ZsV0StT70PYVW3UyERaxn5bTqdkIedsUczry0fqLZbq99Z1xsMjgmbEmjCA/dw3Tyk1TWxvYq5NxsEgTPRSEfUTOqVet+kn2mdUTDAmUkiS0Hh3Qi07coi2mtdEiCnTW17QJGwdmdGaVbOZHAm8qmbh+NG3Eck837O+EOQnJWhM6oRurSCFNhj8V5ey89XCjXnTSGYO0SM+8WCbbx/fq/5hUKG6q/G/zvluugJ3yq/Cl7Ix3RvvbONr0Vkw3bNtM/rgSAMIqGnu2/EFomXft1knNIvJoWmTIBJPE7XUxs1fWHSZCV6HFlNbYVXKBsXcculzXgnyYA/nD3T3RfqhLZ/94Va1l1aZl4su01JuVfJ5xeXPq7NULvTtCVzvwr77wZp2FpL0WGRgdWaWSnY/AfLKwCv/OP4SL6k4ydMxM07x67f87k6suv7GtatsXXiNp2pPcg2/B2elC9RjPwsVH6S8bTcovGPY18udc+aBIfamWD5Dt8uG2lgpo8qKc2ZQ0tiRbJxpFevAnHUmrknVRizCFFfj35r/E2QkkKz8esOi7psURDh5FtlVlG7xG0m2G+p5vF7lprnpMrPQLpIsrJfFS5hsdOrHKrRLGO/3zq5CNpKMPgnrrTs2EaR1RJeKeIBmeVUvc3Ga5iJF0B8pVkoNChj8xEp5EVbsviE9v6FymfSuHlciNRwWlkoDjs4tPZ8db83DO/w0fuU5dl3yrkK0HnXTCaFhk1CjxvDEld0zLcKG59Fyy8LSE880qWVepMvOg78vOzGgZoZrkJsFgmOijcaoJfu27GnL9rfTe//VwNz0rp0LHv/PHV/MyG4LDMCvAEn++Cv/klKxayn1rnrypd528GN8PziLdBPVteOE8uuZFq3wQEjhXg/RzpQQCaLOqltVbLbbvQ5UnWTVeGbrilzSXVk0qMfl7zF+wWE24DW1n0sUwzgP/Ai9ZRYM893WLD21o7pLEQeafj+mVkpJY4n8xv1M7/g9+WGGaKsc96KSTknhwFnkj2HxsXeOzJzqxClcKSRok8ziVlgiSUiEh/Aensxn30tLS87g/Zjd9X01auNUeJFyJ46aa4E5nl/ZCmTc0aK6JnzgIFEEjObiBef6/XyH+ihcS5U333irvjtBI4E/T2/dwETp5CZenFiFJd9UiRCOhHP3HnK4fz8/S8qh2HLN74po1krhvc+nAzi21SPWYhztYCtxlemvqHzidWAWyXinByzLDkGC2kCWB+QCkcXUaVpP+TEnyau24o0hxppo0qy0ZTaTVY7Tth2SBdLXEnAWp7Jc5KIm//webJ7mLueWT0MPUqbyn+KteI3f1oSmU7ej0emAkLDJyrMWWS+czRZaWCr7zKDcFychx1P93Bz5VPOQBfdusSrfgQPzLVJNcnoctKNFaTvWYnazgFz2VN9R2brZwymJX9LhwcLT31w/awaHysTStPGuDbbEaYL90siaT4Vtk9FgD32f9SRBC+G8eV+baoH6VhnP6qtkcbgHj260lLeCBazg/WzWBBh7Txot7RNNosa9d+L3+3Ma9uAUXywd5fst0QKvGx150svXUXG9qghuoRZJDn5iqM6sMltZWIaAW4Mq/ykDgu/rpYme/eWmWZpo9jMOEs07SrrKy8pu6iE9f3btNIWtLHJ3YWy8ePEYbQxcImnYCu8fozVuUJrKc/95ipo8WR44sD51JvysYpdfOUz2jdTflinWyg4aXu7bV14wi02vcuLbcZfmAIW0Wpjv8KGdnnqpL2Znop04wn77BIo0v78AivXC6Zk3pBzvi34YeVrPmjVdoMYub6eWvdkjWOFnmGq4yt2/eqiHTmjLv+xP+vRniWtyK7V0nreO2wVukdbA2LFpbJf3G1W+7p2YV7cfOycdUaqXJQTSS9xGFKbueQNw7X2Jb+Bq745N0etAWGX248Ew32cVO5B8f1S3JB+5DBdS2Tca5I5K4PY5HrgBWSjulo8ZWe325OXt1MkSLjDAuobEfzgDhDqR4HFuztsrf/iNNd22Qw8phaTMR0rZFz4EUlv9I2fB5g6L0G3W9lzutcLnguyOUFmyYlpPRTP2IsYW6vqdGgcaa1rTWvDIX5OWGCNFqcb4L5fOlUMP1ziV2iOyVPjGHcF2J0NJGuAizl8u//tccCdqrE2iwCAaig7LIiBNo1cbZlqD+awOrvD+lTxMf14ezbCslvnrB5RL44Xr11z67cikefAE1QXIB8G2pdxs/t5tlYr9OhmIRMh+zVh+7YKwV+AK6VSKSpBGUCf7dQsQwd/52+fhA20ytQ57RO5P/8GKj7H2A+dAUxfYhPhk/+rmi1wiQz4PrTCDoTfBmSTRa7fXlyymf4D5F0qkSWdXrQktaHquXZ823XfX45L3rpIWLnSS0P0elrRINAEQwBFANVhSSOczHMyHt8TFOaOEPS4kregBRlsT6i828d51MMDmV7LDbayqKpKoSgOoCLRwpfvyzz7OhaK50iv4kUlrC8NHlu6keaMH2+MRhNHjwsJpQvXOFIhEFYH6x67Vo8LN0pSpLx3t3ew/6Upe5LXJPNfRbv4nfiB6fjL1O3hbCTiSaqqjr9ajLqOuko8fbn+tXu3Ct7BWvvioZCOkDY/zyf1v0nzWGRfrQfuIwdHKK19xKFl9ll3XFRMFvbrsPyOGHjBfSW2mX3uCllL6a9jbjxCcTxfcS636WSVx0Kdtu1gXYJMvF6nNdIZ9nyEYZ+IvimR7Kc680Zzj+ZHJIBaXt59KGKU2v5I6+6F0KothSvt94rfO8xq1DYFlp0frZiNN+UmW8+ynVyJZ8tSG3cRcpHgeNKj8HLPUn8aY+NsKvEPqQQPAVR+K0n+hgW+QEtLPllFN37TSOT7f15Suf9x1vRJtvFnP7vlc9iYnedGL7smJvhOjXuxdezU1dMDSr5KTbcnU+Mq7i/Vqr/1WtEtj+KWwzTQFGU0JbPkE3RzuMKgorGSqJSx9Ji+HY/la13S7pzZ84jA7fxeqocfZ0Yb9vtyEcnYw3WnnbCMO5M88XOwtRC45OxhpJNKZTOxbcNhQuL/AWnQwtbjPTewjbCmutQM4jMg5rkZ8nq/ouvTSKYIBWORSNmjArfLUUOPZaBd6mk8lDlRQcCi+VNWzJPlTLFwxykvp2UaGUdX3k9ltPiThockdbepFDTj19C0ueceo7Zp6u5S3AFRPvI2oFFv/bBVEMawzr2ilhx421f/Aggck231QdPJprDf/Fpf1B3Ad1dELJmQPAnAcewAPnojC/4d6sJO4PSPRq/uoDwExBQPeb+evZz//5qLC/BnGB2pFN6XVhdqv/vgR59+UONkpe1DaS8Amuz84ykFCjG0XBTwqwwTDEX4YAzEAhoOeqMN2Mb+WKd3tDbPBX3Hr3aM8Aihydd6+TBkqgnbu2trbgn/Oie52q4kLhFgHy+2Io+yeb1Me/yBfhQJ499yfnjoxpfYZArqK3mTDBbpre30QvOhm5JY/1FXbtgGbOXcHt/p05Xkz/wl5gn8gRDyBqovfD8sqsnnfZlfQ6v76ZX//5oaG9YoBWybn0qtcxX+3F2BrbrIL0opPJZRGU01mGOYD18zy6E1D/HFBxpJWVk3zOxeodfe7VDz+5osdX3NGn+xkCOZd+y6/ycs8ie3F00kgajhXlbOmBZ/Wex+rSmR+ryNebkQhjuI+0wvyTPTvLXZ0P6T6zIZN4wY6BTx3j6MTMDBB5g+PCAjHcSU7lMRAow3q9z+BW6ahwz61M7/A4nmHwGDIhWy+WObbTu04GWB6/gK2FMBR4nJoZBBI03ImPnOiTzPlMn1LwHgiFraLMSK2CyP5bRe9nQvgX6zn2WgXpXSeTiKCeblA/4j/5g/8AiidfsO31w4mpFTxwynAbp2zldLmPt93a8w2/0Csy6QNOO5sZrrpkRa0BzX+gZ6SI2cfjjFWBA3vGRXdPFr/h4GXi+JMjMDpsjQcpLNXi+YHLxPEnOBruuOWMZRbyYnqeepKGNn5KDi7JP8VXprWyi5wfm4DFIUyw8O510im567Xb6+rzUMb4MbYYyKTzHzqbD/mN8NiPoNLfniatcXTSGXnvWqberhau/lQpeb3efNA0ZVKfONyiD9xQJsDjBnB6YwSWKt+BX9ILl++wUStkUn+5l1tfs+EBtLphF0em3O+Kt5XS6bLjTxo5VLYCuV0wOq3n9p4HM2T9c6ajWpJ+WUd2cLRGH3KDbV+D6jC4d1/faWAvUoKLQqFanbg+r94Q/u5pMfOulf77k036Fxu4TIyphBx/YiLrhnD+0xHs/qowG37fOkPy559+CDv77eY4HiwKrRJ3UjOzGcefILj6YG3ZRG9aULXDUOhSmd3xwvn5Icg75+yHX3p/WD6U1tIzbVvXtP5bMmZMJzdodH/S/7Mbca7oXy2TXkEgK3zMZD7sivf/yR/CJC+cLm8nsFc1DeewKlw4r7+yEaNzSl/Z93qH4E4cf9LEKhSF0vXm5lVlNQOswi9qsEl+rdXaK0iYlNpFq9rvibXkRPgTfXIOu1BA4r1hhtnhlY3Fw4RrNiAy+RX42erGsIXUl6w1aYjYahXdn4x7HGuskmwP8nU4TI4rMmzB7fJsKny1eUgN5Eprv3Zeqe9oqRZTfQ8RO60yGf7ETkphQc0rQAg5j7L5cmI2v7OpsksfyKf8nD7ZXTuCi1ujMfSrL0xQfGLPDV+//09FKy8xDO+KF8g+f0WY8+XZxMU6FyptRdIare48Rvjg3Uw6XUxX7RcKzsyE/4jGiI0Rwx6rTEb7ia0l8ZN8seK52gJuyceRI1j3Hyp+EiwGcJa7xz2VPOVWfqnrW8f7DPN4sT597+FZBjTteGNAt4s7xlarOPGJFeyPpNc8sZUEn+g1HeMeD7aD7hs9mPAv+LU2tzN5rlaPDrZaxYlPEOz/2v8m93FmMuKTtxbCmFuGOP6zT7zVKmbGKj5RoUD8kAdtKJ2XR5WBWGWc4hOlRDim8vMjaN/gn/8O4Cfsi7bO1TiWGFYhnGEV8rk/Vhknf6KeEzIPy3lgVuBpJQtRIedmsm1vyr0TDKtQdKtAtj9WGaf45OfSpux237sfWJfrzvX46CXq+Zmn5fqZ7wjDKqmqVe76ZJVx8iefy7e0NjpFf4zHPPz9eHyzYT1sYBwvE3cHd336zGCsMk7xSSUtf/NEeFJr0PLD0nG0ac72gSEzoJ1LH4d9T8ewyszzqJD+WKXn9hO7C8AOUDMlTvExXFnJ4dSLNOUGzVKlZPtw2lcQowBR1/c8mGbKGZpVStQqqT5aZZzikzOO3ZM1oirZW1GSpHK5sgzqH+tRA0PvrRSdOtWX1hsehlXi1CpMv60yJuUONpV/zV3Ap+Pd5zu3xJ6BLITmF627EqTaLregRI9W/DIH8pDMaFhl68en4x27rWKl9xMc/MAfHP39VZMuPVDiF2FV/+764JI3wSgAP7obYvfDZSy6F42vcZJLNa9FMQSrwAEVC3fpKYGNVrHSu06GBJluuKbDaXLHWFHUZ0ihQUFsddAjr5pBsUCsghUfxH6rjEl8YiF2UnvWXYFhEwd5iIIAIn2MQywq291i0RPJO65yjr3CoR9WGUedmC1if87phAN9XXF0KNE4xDn7L0v3xMISCXmqU4HZb5Ux1IlstshQLtFh6XkBeqqU6O/h1noMeH39hWkj+9hvlTHUyb3ZIvbnnA7YcFF1VIUSBU93QXC/MK/TYb9VxlAnDRaxP+d0gP+MupMog0FSnLy+sOtAWLjEx+KKvmG/VcZQJw0WsT/nvIoMqQguCaxgfTiq1deVHi7BlJeUqj3z7LfK2NWLAS2yKopVi+g5x36zvAQHIUmj/oQ/AEXDwWGi7V0+emEN4HlOJ/utMo46WTNZxFZjdAwnUYeisiBzKBS3dc62oWO/Vcaw3Gng7SUxvoNRhnXOLyaKUwrnAzxGs4prJBpQTLzdKlbGXSdvzzln9C9hTXwZ7vPfUyhE8vSCBFEoedfbv4at2P91xrHcGT5kK+dVZWw2ieB9RK96Fhz8PEcDxdFJbwRq/TwiWlnFVpRsf+7TjgrjXu4MHyLpNnQPY6qjweHo5M0EpGP8R7yTbMtJPrdBEfhqNLUtfBu1eo99ODqxg6kz/W7PLvfdumdScOJYWzjIcehTtO1JjVJ68SejNBOZwT5U1yQYGgG3YUnijdrfeNEjdlqFGb2LPqY8FY1WUE38NjJKsQfUWy/+xGBEBGZnpnkj8TPDmrvit5Rl16Cx3Sq968ShiQO+YvRe2vWVRyQb2cUo6+R7URzANIrmicvfTHC6aChFcZ9Kk1T8jLJOvKCd99/WV1CfuNwOlIoRp0QlsZydmAaVUdYJDuI193EcD7igUFUKdSq3lQmRyijrBJd4VMaxQSIonFXbpaKymy1jr7dxZ5R1wmIPZWksA8IDtxCrdRZSyqIofR9ztzLKOgG99uAeS6EA2RfOXDWpaNI2W/r2fYz9ykjrJKDPKeJ+/aaJNozZR17lwCWcHT93QVR3t8XT8tEAxWKnVUZaJxA5xcft8fQoyMFXoRCr2ziqrG2LxdLrwh85Rlsn8EWv77hxcfs22Jlp+kJknxdO626Fova/65vtVhn1+8XBLE4asVYpj9rQh24gXwByik+ttRri3Cljxoj7EyoU3aPIrmHfMXkrgaDL4z3zHFODk/FrFBp9nUDwFGs9qm98g5Q6B+xX3usV3lomKKfFQVtj9HUCX4J64e4uj7tLsYtcOQruAdz5MjMGOoGIrEdRim/AthlV9BEh5VLfso0s5w6ttfdx0AkE3B79LmxZHMMapf0Qvf1R9fWn27Z8qElPuIJmA2OhEwB2S59lRNsWbS+YF6Ef4yz7StAoiXdZyValyHJey2Ufw7+1oMs698+Y6ASIx+gCpLnt7gGEAz7HbUGviKwPGQKJ/WbN+L1CNUJUpljyibN716rbOpJ9XHRCM9FCTP+vuEXFmo3eXeAS+HpqXLndrVKTNWp0YxVNI4lbWXOF/oPj0k5JM03IrjM+OoHAvmDkIq18a1s2Glu+SB4jz6tltvy2u9H5rJR/lBJpLfHHzYGw/0fKlnyWY8ZIJ7Tw+VowlBLd3RKPjHi/MdPoiwG8DwKsXO2NC8o2W5Iaco7FleRwPuSWaPRlmkfWrk6KO8LsAchETLKXyxfjW+7oRL4Kx4ZxtDVfTSo1csoh3ghqZ5KJIxDcrtTuu6jS1mlFae1l5exF6UhusgvmqXxpMwcX2m84+N8mI+C9EQFm5Y2ThomD8GbRqN/faYJ8zXkkI+7U1kA88z9PwQUXH3dAS0VeOCVZXwGax5ULYPTvbXVAEFy/1qu1tagsbxVZ5lS21lWOtG34KGUsM7Rosivvj88mluHT+T/UfWinyjLVw/x1aYu1vsPY+RMkwPKFqlMBbTfqdR/py5zIsChdq7L/qCEEOywfynBYOZbh+7ds6keZUBTlqEyRvpkPHFfIviC46ldRkda2iiVXAshzyJI/2IZrCVKVxtj08FoR8xtiyKsdh3HmysB2CLNPYDOnT9xvYQx1Alj8LJw+ewNtbZt4hSwObIrGNW3Lbfa+XH6lJCvHSimn7sDNfuI8k0jAj3X6mPhrOmysIS5e8XDPE2TTQkiNejmWIeRcOUzJ+uLocep8SUPUQpYll08FQf6xKaYx4TKT4HAasv1WfpadIqWwNfEVCvpLjMdh4VlgXYWzcm2BCFBZdyyiwRTE/cqSxsg1/aeePsjH22RFPt9lvb5CYMFzq7hnbheTkix/MYI17Kntqr1P7wzVKh6GnckGyPWzPSgEXLNK2K0oFVabojtUUj5K+Z7X9piDU0nlfJ4HKb9QwerNFSMtAszkziKNzqPCVli+lXbGheBXSD3iMC2DqB61RIGU2Zz0XBgvgW/vSvjvy35sQw0EUgIbjcj58K//URdtjenHnAiw29oPbqupez9Bw+AyMNwuQJE9NqIP5TwUub5lwbWuMbN0WyYhrBaQZMv5tcdZJ5QIr4l320pDe6ooKG73g+8YV7uC3exxxbd6ua8pyz+pfRJelwBiYspfBjLmp94SomsglXKFeUsuEAypUONUww8u7FUXExFpKpWRv+L2ktef97cb8zae8UkDUZekFS9ri5uI+nInIBSeOxfuLbEfVPn6fJ0+/ydB/EBFsqQo2pvapkaayPKSJnsL8eIxx5rkgisGmWLU2ZOHzIdpniMhr17944vB9gt/9KKTt3azsR0/gSW3IJx6qFXQGIhgdC7MCRsFNyF/lQ9SgPrWY/gJcP8x9Pfs7Lilf+2dkbPKbBQ23B7B6y2celzoPA2VxGtftLBOgCUBJhgK66418NLUpr3oxGA07OKv5xfyialocWP5JBA1HE2I3PO/VCL84TYwtbxE994o3OaXL1/60uN25KxCiWwyJTkeR8NQ86zWGpz8U2lg80Ckv60qwhZ618moIQNXvNWfxemPcGpEc0la31vnjmDjCGVR2mdpbWSFHE1ibNIOGfzUMFHqX6lWwPX4fO6F3dmftKQJ7rYvbp6ZGJ3IHJQgisuA0r8oxDYNc5TiM5EAeES3HuDdwC+e2k3DUOW9QGMPNAw+jUajDFcvXQIurrnhtQ0TlLEUlVTtQXPNSjWPHPy5ojVnuZLVzfNZZQLASYmPtde8Bzg0DKCbzcP6tLlE6qLK1/mRIw6eSFwfGnN8vO0jtYYzsoWPtWxDWKwsR14K2CYXmoe2OyhhWjMxOgE4W6Yq4eDMvy1PWAvam+CApUVx1KWd7ed6lsnkxCcAZRq/ukhlf7tL8WP9uPUN+QlB46hhwL0Pb6jhdWfSkWbdW6bmsOFOzaRBxA2FfaNDeOPLR4mAKGyr1s44DpRppjoLcu9MkD/B8U/MW+0xmbw9XuvJrjf6pGnG45DR/jCXxsPQGWWrvNnN9qQTh3HjzcWGoxOHTnB04vAa2F3S0YlDJzg6cegERycOneDoxKETHJ04dIKjE4dO6EUnWX0li9HiszVh4Ey2VXrRicHI2MU+Y9jApFqld504vCccnTh0gqMTh05wdAI4B1PD/EEOzTg60QPPhDXRoRFHJw6d4OjEoRMcnTh0QrsOcYd71ll56viW0uXa41FTs9LuT0vC4vscfjdZtNPJzgs9b4kQVWqPu9adUNZHapopNmw9TzvC6ItEQr2T7+ceuoU/AOQD+oN1z6AJVMRS7RF+VRM9xvnWpmyb1YhWqD4n1QVS+oH9Vmmnk+u+rWH3POmgsmZKRYwJbgDi1VoqOaNWLlPTon7ajKy/IdTa+sOwUTNAhb9BH5cShlmXEvSZfr61WcDxDBmaipPKUf2Uq8kMXK5iAPAfvggAJ2jEKUlq16ZNlm136aAfVmn3YUWcIWJIoJ/SWTMlGk5Jn47gAz7ovoeQ5b/qnxV8aPJi9nO41+aK6Zy5saTGxyQL7tXL1cupzNQVwB7U1+PA6ncUp90wpQFKaUlCCeknBoCzuODTV0Z44JcxeWCzM362Sn2Ww/bUJvZ7ERJlsq1N3NmQ2xVTy4O7sn3qrpTrk0ZQR4C/7SMd+4ijUeV2XudFjtYAYq1nr2uk1NPyK+ZJWzF//1i8WbwxNjG2Q+PjpP3oW9ByWKJXj67515cQxNrPcxL6q8Ri5QEfOpreKb5tTWkgx4iuafLpqY1O+gXqD8sSzeWuMJvwR/ykVhVlU1lHGIyuJQLrnZXOHevk1J7v147qZOxVNdG/1eqO2mitapVBn3q9+h/Bw5IA89BQe9ULa62j0dav6EQTi67pduVO/7BkeXTLVWpfV0MjgeGrNmtpnedlTalF10Ua6VDJyD1FyM30uSx+vsjoGsx5t4N8vGxN6ILnif1eYvA6eR1jElRoMSmeoSD9O29haXb1yoWLViUjYmz8h/qYNwlGn5B28njZm1QZRZ2051lBVfRTTGFpj+exraptQyGqmDX6T2SM6YcdumS8dNIS6zRah3hW22rrokp9nn7YoRsmQCdWTD6HuhoOyvvmOscrJZVDayZQJybqriZ1s5KgPkYhZceh9MJk66QOVYwRFTfPvYwtLx1UKd43zv1ivVmrbfzrYODoxKETHJ04dIKjE4dOcHTi0An266Rx9VOHOvlc7sSaNi7YWy++nblX4ZLdffUuipSSandDKbe3qyVXRVR26kl1Gg8dW2LR32GASuLR12as0GibxFZ/omp3KkAw8PA8b2kbbu81V73Pz7UWKapljfdfmQ6p0Xjo+CKgTCCkTXmxt0kzI24SO3WiJlElQQ3Kuda2aOC89uQ6S18mMDhffxu3/HzoeOMpFtMAZbnVtdcZYZPYWO7celQgLh/kw3ch4Ur3rrjOaRXT08YOn7dhBirhaVRZdU2/toeOMxpAbpvmJTnVsn/fqJvERp3wBSDyEsB8bCmVWKYlcnZerTwyWJKansJ15D4bNgUwfBYIO009m5zF1ZXaHZokyv3ME9OyvB4LZlWPzAFThJDezU4mT4VHxlvGExoDk7BzZbsWemeKmVnsfAfB+9BjcQZ8NA/JLBPPNDzNSiIR6CapzFheNg3cJnZNb3momlc0ATyezHT9A20iSc+fsTG/tCYPGqmo7kdXKTPFU5loSYnwHkalHz56JmlElljevvjkVoJwEv+rqeQJuwPXSch46dsrmvlpUiSQNX8ofdnSo/F0npZVbQ69fVQzuYU77Xn0yziiVZ4Shaxhpf/uADBaoa5hLExin07gCWAD6zw3lZkZ/wl4SCYQWlS1kMv89EaFp91F1fSxT1Cq9QimtDlUKmQCe+zXe5hvGwSODWmWamMeMvL2XBp1MhYmsVEnU3oUHtf7BLn5WxcsUSO4WOBzpqer4NnjwDVlflkGe49TYldgfpXp0NgCWZCOjo4+aYl+O9k+kuH5EHUhITEFNORnV0GbBY8cGwuTMLYtGFNiIR2i3nE+cqd3BSrVM4XpKUClNlzUYNYFmnGiSc/TZZtDhd9QFqanp+8IVJ73jh3hhxnvZ24OQg/0LDKzRiJWcEfbJPo6PgwsWtN7ZK0EofwJrLncSyV0U1PA00xB7qFcqD99wFwFMJepvy4JobTeSFBRQ6LpVeZD5/cgk3qcu7srLY/7oPb5dBrc9P+CSM+tmjYGJqH+xJrUKyUvuF0pGnepKtytLBQgQT1VOAyq6elaDILFk5hp4VjOxYCb02hg44cMaXforRYK7Lu+LpX7nHf6z/V0WHcAtxjeq1QC42ESds5dtEmP/vuMwCrSH6+m1/6FqzAnl7IgKNOmp/NXgidAaIguFKuVQH82I3C5ayUngZdtd2hmTsw9pEN5j9rZGL9uGFi92BNWKsdeEbIfiSQJTAVHkbgqo2iSBrTU1NM8Bze2mWgZGBWwNYYsUsUzikaob80EVqxP9WH69SJ3+SEdhiA2VCtL7Q5dS2Lv1lvINHdvHR/KZX2GC/KBVm6XUsZsBTeB8TCJffEJZbmc8qbTpJTXHeP/tEgoTQIYnDc+TXsqC0Gx3oz4v7m5YNpDxDw6tjaHzufpMVkSqI0jtRGcuVm1JtrOQpLRNC0jVOQ8jVHXEgteaigfw66MokmaIZ+OufoEA+8TnDLDGa3RFvVn9O+Brf7EYWKxsb7jMME4/sShExx/4tAJjj9x6ATHnzh0guNPHDrB8ScOneD4E4dOcPyJQyc4/sShExx/4tAJjj/RJ83X5813aI+d/U/GlWK8eUZjh0bevUhgYueZthVmZBZ6dxhdPmPHeAeHV3HiWIdO6KJerE+o5cyq9T4x+ZNsdexzG7KxVPWhK65ffleH8aDuT27Fgj4Di3x3Xn1oIMbDrPHQFbdcoTYjUPN7OowNDfGJfh3vFf7EeGhAOA0rxkOvNL+nw9jAwU1t0VTgyQ8ft8JINBEfYgs3wQTD7sSmbz8C3EREDmZl+nDtTwjrBP6dfwgXVfeC9vDIB5JRLv38bAroTvejtJsMHTPTDG7hoUF8Y6TNG5gG7DuMGuzUVDmkP8uDlNmUCrM0UhV8+MCKDDOnaaEsW6Ilzlpil8S89EG9zq3L+RvWlYkmp8p+sch42YcZ5VbTas88/L8Rt8RC2fPo9QKpVA/l8Y3pB7V7A091LgeH0UJLTQnEXN8hrkfGgxPOhYwHEGbuZora2tyeqdiRK8TlWxDdWoYLzaXn5oHkmbRvaT648fwslZwXZgjjkQpPS/QDFquHzunvCW3foNsI2WGAWNtP9MZZPU6hD6XTtd84TRLlJozFDn24V8IEUjJfAgG0WX1BPo3GtneZ6+dnTEW9w5GjD1ljlF39UHzjtm/gNPmNMC+2n/DbsT1jFpbYXmYDYhJ9KFXcIRq+3PEggqRU2kwO5oKIAiSEh+Biu+ZDO3sDh9HC7E8wPihDidf8V/gwD+JPz4MvEqv8MtV21kgpWZDCPAlKhWxWql3m+uwdBMouRizeEv4gGEgTAjfVQx/oe+Yqv9q+gT4Q32E0qfuTUuhxA/gyrJVdoD9oQNbTT9VZuWYlTsYHgKA/Gisv7tB/59GV1VKotFPiFdA+1J+5F+ajVCyPSj4S90ZKtUPxPXG63HZvgLMMOYwoJMo8bVgTDWgNJj+QOWwdRhr1ZzTUUN9ppo2CHN4d1vqOiYUkFjMODtDYHmvlf9YEh3fLC/7EweGZl+MTBwcDx584dILjTxw6gQtURNPaDDq/LNtWrB30Wcv2a+v5zBCt4eeVvpSmBYx0TEtbOQyAB4B8gFMzUEgsNfw014Yb91u7Klm321agqlh1edv49kuW3YxFVq+17zNwuXq5Wn+w3l/8z7Jt/bym7g3WiXyb7dNInxdme+3j7eaG0KxJGLf24S98+Gv6EdC6L/zkLG+0Z9l+bdLehGU7atm2GdGa8Appa8IHa8JwsfpX6/ZrdHt84e9idot8tSa/HauOrFgLjvubxYYfWL1s+LVsW4s9/kMu8Nf0d2zZb/U/1mmVre7wVTt2K7w+I4iWH8t+TGj4s+xvotG705/N7FI/dNJvZE7mag/42yV5vtTwY7WbVcbww7L92hrTl9YEC1ahW7GWy5aC0LrbyurjdMOP9fh5y36jO+OLaNP/BwH3aA46qOniAAAAAElFTkSuQmCC>