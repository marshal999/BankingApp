package ir.challange.bank.transaction.mapper;

import ir.challange.bank.transaction.dto.TransactionRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TransactionMapperRegistry {

    private final Map<Class<?>, TransactionMapper<?>> mappers;

    public TransactionMapperRegistry(List<TransactionMapper<?>> mapperList) {

        this.mappers = mapperList.stream()
                .collect(Collectors.toMap(TransactionMapper::getRequestType, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    public <T extends TransactionRequest> TransactionMapper<T> getMapper(T request) {

        TransactionMapper<?> mapper = mappers.get(request.getClass());
        if (mapper == null)
            throw new IllegalArgumentException("No mapper found for: " + request.getClass().getName());
        return (TransactionMapper<T>) mapper;
    }
}
