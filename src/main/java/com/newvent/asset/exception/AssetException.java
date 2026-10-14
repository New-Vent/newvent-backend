package com.newvent.asset.exception;

import com.newvent.common.exception.BaseException;

public class AssetException extends BaseException {

    public AssetException(AssetErrorCode errorCode) {
        super(errorCode);
    }
}
