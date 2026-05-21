package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.bank.BankAccountResponse;
import com.example.expense_tracking.dto.bank.LinkBankResponse;
import com.example.expense_tracking.dto.bank.PlaidExchangeRequest;
import com.example.expense_tracking.entity.BankAccount;
import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.ForbiddenException;
import com.example.expense_tracking.repository.BankAccountRepository;
import com.example.expense_tracking.repository.PlaidItemRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankLinkingServiceTest {

    @Mock
    private PlaidService plaidService;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private PlaidItemRepository plaidItemRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionSyncService transactionSyncService;

    @InjectMocks
    private BankLinkingService bankLinkingService;

    private User testUser;
    private PlaidItem testItem;
    private BankAccount testAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@example.com").build();
        testItem = PlaidItem.builder().id(1L).itemId("item-1").accessToken("access-1").user(testUser).status("ACTIVE").build();
        testAccount = BankAccount.builder().id(1L).plaidAccountId("acc-1").plaidItem(testItem).user(testUser).build();
    }

    @Test
    void startLinking_Success() {
        when(plaidService.createLinkToken(anyString())).thenReturn("link-token-123");
        LinkBankResponse response = bankLinkingService.startLinking(testUser, "ins-1", "GB");
        assertEquals("link-token-123", response.getLinkToken());
    }

    @Test
    void completeLinking_Success() {
        PlaidExchangeRequest request = new PlaidExchangeRequest();
        request.setPublicToken("public-123");

        ItemPublicTokenExchangeResponse exchangeResponse = new ItemPublicTokenExchangeResponse()
                .accessToken("access-123")
                .itemId("item-123");
        when(plaidService.exchangePublicToken("public-123")).thenReturn(exchangeResponse);

        AccountBase accBase = new AccountBase().accountId("plaid-acc-1").name("Checking").mask("1234").type(com.plaid.client.model.AccountType.DEPOSITORY);
        AccountsGetResponse accResponse = new AccountsGetResponse().accounts(List.of(accBase));
        when(plaidService.getAccounts("access-123")).thenReturn(List.of(accBase));

        when(plaidItemRepository.save(any(PlaidItem.class))).thenAnswer(i -> i.getArgument(0));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionSyncService.initialSync(any())).thenReturn(5);

        assertDoesNotThrow(() -> bankLinkingService.completeLinking(testUser, request));

        verify(plaidItemRepository).save(any(PlaidItem.class));
        verify(bankAccountRepository).save(any(BankAccount.class));
        verify(transactionSyncService).initialSync(any());
    }

    @Test
    void getUserBankAccounts_Success() {
        when(bankAccountRepository.findByUser(testUser)).thenReturn(List.of(testAccount));
        List<BankAccountResponse> responses = bankLinkingService.getUserBankAccounts(testUser);
        assertEquals(1, responses.size());
    }

    @Test
    void unlinkBank_Success() {
        // mock return the bank account successfully retrieved
        when(bankAccountRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(testAccount));
        when(bankAccountRepository.findByPlaidItem_Id(testItem.getId())).thenReturn(List.of());

        boolean result = bankLinkingService.unlinkBank(testUser, 1L);

        assertTrue(result);
        verify(bankAccountRepository).deleteById(1L);
        verify(plaidItemRepository).delete(testItem);
    }
}
