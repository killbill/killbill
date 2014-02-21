package com.ning.billing.invoice.tree;

public class TreeNodeException extends RuntimeException {

    public TreeNodeException() {
    }

    public TreeNodeException(final String message) {
        super(message);
    }

    public TreeNodeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public TreeNodeException(final Throwable cause) {
        super(cause);
    }

    public TreeNodeException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
