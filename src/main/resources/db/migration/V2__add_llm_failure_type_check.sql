ALTER TABLE llm_call_logs
    ADD CONSTRAINT ck_llm_call_logs_failure_type
        CHECK (
            failure_type IS NULL
                OR failure_type IN (
                                    'VALIDATION_FAIL',
                                    'TIMEOUT',
                                    'LLM_ERROR',
                                    'STOPPED',
                                    'TRUNCATED'
                )
            );