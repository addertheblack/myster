# Myster Important Patterns

This document describes key architectural and design patterns used throughout the Myster codebase. Understanding these patterns is essential for maintaining consistency when adding new features or modifying existing code.

## Table of Contents

- [Event System](#event-system)
- [Promise/Future Pattern](#promisefuture-pattern)
- [Listener Pattern](#listener-pattern)
- [Dependency Injection](#dependency-injection)
- [Threading & Concurrency](#threading--concurrency)

---

## Event System

Myster uses a consistent event dispatcher pattern throughout the codebase.

### NewGenericDispatcher

**`NewGenericDispatcher<L>`** - Generic typed event dispatcher
- `addListener(L)` / `removeListener(L)` - Manage listener subscriptions
- `fire()` - Returns a proxy that forwards calls to all listeners
- Thread-safe with `CopyOnWriteArrayList`

### Usage Pattern

```java
private final NewGenericDispatcher<MyListener> dispatcher = new NewGenericDispatcher<>();

// Fire events to all registered listeners
dispatcher.fire().eventMethod(arg);

// Register listeners
dispatcher.addListener(listener);
```

### Common Dispatchers in Codebase

- `ServerEventDispatcher` - Server-side events
- Type-specific dispatchers in `TypeDescriptionList`

### Where to Find It

See `com.general.events.NewGenericDispatcher` for the implementation.

---

## Promise/Future Pattern

Myster uses `PromiseFuture<T>` for all asynchronous operations to avoid blocking and enable composable async workflows.

### Basic Usage

```java
PromiseFuture<Result> future = PromiseFutures.execute(() -> longOperation());
future.addResultListener(result -> handleResult(result));
future.addExceptionListener(ex -> handleError(ex));
```

### Key Features

- **Non-blocking**: Returns immediately with a promise of future completion
- **Callback-based**: Use listeners to handle results and errors
- **Composable**: Chain operations together
- **Thread-aware**: Result listeners can specify which thread to run on (EDT by default for UI updates)

### Common Use Cases

- Network I/O operations
- File operations (reading, writing, hashing)
- Long computations that shouldn't block the UI
- Background indexing

### Where to Find It

See `com.general.thread.PromiseFuture` and `com.general.thread.PromiseFutures` for the implementation.

---

## Listener Pattern

### Convention: Use Private Inner Classes

**Pattern**: Use private inner classes for listener implementations instead of having the main class implement listener interfaces.

**Rationale**: Keeps listener interfaces separate from the class's primary public interface. Makes the class's purpose clearer and avoids polluting the public API with listener methods.

### Bad Example ❌

```java
public class MyComponent extends JPanel implements SomeListener {
    public MyComponent() {
        someObject.addListener(this);  // 'this' implements SomeListener
    }
    
    @Override
    public void eventOccurred(Event e) {
        // This is now part of MyComponent's public interface
        // Anyone can call this method
    }
}
```

### Good Example ✅

```java
public class MyComponent extends JPanel {
    public MyComponent() {
        someObject.addListener(new SomeListenerImpl());
    }
    
    private void eventOccurred(Event e) {
        // Private method - not part of public interface
        // Implementation can be large without extra indentation
    }
    
    // Inner classes belong at the end of the file
    private class SomeListenerImpl implements SomeListener {
        @Override
        public void eventOccurred(Event e) {
            MyComponent.this.eventOccurred(e);
        }
    }
}
```

### Guidelines

- **Inner class placement**: Private inner classes should be placed at the end of the file
- **Optional delegation**: You don't need to have the inner class call private methods in the parent - you can implement directly in the inner class
- **Large implementations**: The private method approach shown above is preferred when the implementation is large (avoids extra indentation)
- **Simple callbacks**: For simple one-liners, lambda expressions are fine: `someObject.addListener(e -> doSomething())`

### Examples in Codebase

- `Tracker.TypeListenerImpl`
- `TypeChoice.TypeListenerImpl`
- `FileTypeListManager.TypeListenerImpl`

---

## Dependency Injection

### MysterFrameContext Pattern

Windows receive dependencies through the `MysterFrameContext` record rather than using static singletons or service locators.

### Pattern

```java
public class MyWindow extends MysterFrame {
    private final FileTypeListManager fileManager;
    
    public MyWindow(MysterFrameContext context) {
        super(context);
        this.fileManager = context.fileManager();
    }
}
```

### MysterFrameContext Definition

```java
public record MysterFrameContext(
    WindowManager windowManager,
    MysterMenuBarFactory menuBarFactory,
    FileTypeListManager fileManager,
    ClientWindowProvider clientWindowProvider,
    Preferences preferences
) {}
```

### Benefits

- **Testability**: Easy to inject mock dependencies for testing
- **Clarity**: Dependencies are explicit in constructor
- **Flexibility**: Can easily add new dependencies without changing all window classes
- **Avoids globals**: No need for static singletons

### Guidelines

- **Use constructor injection**: Pass dependencies through constructors
- **Avoid static singletons**: Use dependency injection instead where possible
- **Service locator pattern**: Avoid - prefer explicit dependency injection

---

## Threading & Concurrency

### Thread Categories

Myster uses different threading strategies depending on the type of work:

#### 1. EDT (Event Dispatch Thread)

**Rule**: All Swing UI operations must run on the EDT.

**Tools**:
- `SwingUtilities.invokeLater()` - Standard Swing approach
- `Invoker.EDT_NOW_OR_LATER` - Myster utility (runs immediately if already on EDT)

**What runs on EDT**:
- All UI updates (setText, repaint, etc.)
- UI event handlers
- Result listeners (by default in PromiseFuture)

**Critical**: Never block the EDT with long-running operations!

#### 2. Virtual Threads

**Use for**: I/O and long-running operations

**What runs on virtual threads**:
- Network I/O (socket operations, HTTP requests)
- File operations (reading, writing, hashing)
- Long computations
- Background indexing

**How to use**:
```java
PromiseFutures.execute(() -> {
    return performLongComputation();
}).addResultListener(result -> {
    // Result listener runs on EDT automatically
    displayResult(result);
});
```

#### 3. Synchronization

**Rule**: Use `synchronized` on methods/blocks for shared state

**Guidelines**:
- Example: `DefaultTypeDescriptionList` synchronizes most methods
- Prefer explicit synchronization over implicit patterns when state is shared
- Use `synchronized` keyword for method-level or block-level locking

### Invoker Utility

The `Invoker` class provides thread scheduling utilities:

- **`Invoker.EDT_NOW_OR_LATER`** - Run on EDT (immediately if already on EDT, otherwise enqueue)
- **Custom invokers** - For background work on specific threads

### Examples

#### Good: Run UI Update on EDT

```java
Invoker.invokeOnEDT(() -> {
    label.setText("Updated");
});
```

#### Good: Run Long Operation on Virtual Thread

```java
PromiseFutures.execute(() -> {
    return performLongComputation();
}).addResultListener(result -> {
    // Result listener runs on EDT automatically
    displayResult(result);
});
```

#### Bad: Blocking the EDT

```java
// DON'T DO THIS - blocks UI thread!
public void actionPerformed(ActionEvent e) {
    String result = performLongComputation(); // Freezes UI!
    label.setText(result);
}
```

### Where to Find It

- `com.general.thread.Invoker` - Thread scheduling utilities
- `com.general.thread.PromiseFuture` - Async operations
- `com.general.thread.PromiseFutures` - Factory for creating promises

---

*Last updated: February 2026*

