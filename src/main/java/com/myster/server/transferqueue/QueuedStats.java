package com.myster.server.transferqueue;

/**
 * QueuedStats contains information that might be pertinant for Downloaders
 * waiting in a TransferQueue
 */

public record QueuedStats (int queuePosition) {
}