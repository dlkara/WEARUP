package com.lec.spring.domain;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class PostValidator implements Validator {

	@Override
	public boolean supports(Class<?> clazz) {
		return Post.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		Post post = (Post) target;

		String plainContent = Jsoup.parse(post.getContent() == null ? "" : post.getContent()).text();
		if (plainContent.trim().isEmpty()) {
			errors.rejectValue("content","내용은 반드시 입력해야 합니다.");
		}
		if (post.getItems() == null || post.getItems().trim().isEmpty()) {
			errors.rejectValue("items", "상품 목록은 반드시 입력해야 합니다.");
		}
	}

}
