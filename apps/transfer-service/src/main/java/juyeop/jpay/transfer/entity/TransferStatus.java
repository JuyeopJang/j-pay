package juyeop.jpay.transfer.entity;

import juyeop.jpay.transfer.producer.TransferEventProducer;

public enum TransferStatus {

    PENDING {
        @Override
        public void onReplay(Transfer transfer, TransferEventProducer producer) {
            // PENDING 상태는 진행 중이므로 재발행 없음. 호출자가 로그를 남긴다.
        }
    },
    COMPLETED {
        @Override
        public void onReplay(Transfer transfer, TransferEventProducer producer) {
            producer.publishCompleted(transfer);
        }
    },
    FAILED {
        @Override
        public void onReplay(Transfer transfer, TransferEventProducer producer) {
            producer.publishFailed(transfer);
        }
    };

    public abstract void onReplay(Transfer transfer, TransferEventProducer producer);
}
